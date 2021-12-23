/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("CovariantEquals", "BlockingMethodInNonBlockingContext")

package com.epam.dsm

import com.epam.dsm.util.*
import com.zaxxer.hikari.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.jdbc.*
import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.transactions.experimental.*
import java.io.*
import java.util.*
import kotlin.reflect.*
import kotlin.time.*

/**
 * Can be init by:
 * @see HikariConfig - this useful for production
 * @see HikariDataSource - it can be useful for tests, you can close pool connection
 */
class StoreClient(val hikariConfig: HikariConfig) : AutoCloseable {
    private val hikariDataSource = if (hikariConfig is HikariDataSource) hikariConfig else HikariDataSource(hikariConfig)
    private val database = DatabaseFactory.init(hikariDataSource)
    private val schema = hikariDataSource.schema

    init {
//      todo transform schema name?
        transaction {
            execWrapper("CREATE SCHEMA IF NOT EXISTS $schema")
        }
    }

    override fun close() {
        logger.debug { "close store client with $schema..." }
        TransactionManager.closeAndUnregister(database)
        hikariDataSource.close()
    }

    suspend fun <T> executeInAsyncTransaction(block: suspend Transaction.() -> T) = withContext(Dispatchers.IO) {
        newSuspendedTransaction(db = database) {
            block(this)
        }
    }

    fun <T> executeInTransaction(block: Transaction.() -> T): T = transaction(db = database) {
        block(this)
    }

    suspend inline fun <reified T : Any> store(any: T) = run {
        executeInAsyncTransaction {
            store(any)
        }
        any
    }

    suspend inline fun <reified T : Any> getAll(): Collection<T> =
        executeInAsyncTransaction {
            getAll()
        }

    suspend inline fun <reified T : Any> findById(id: Any): T? =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                findById(id)
            }
        }

    /**
     * Using for find elements where an object stores a list by 'jsonb_to_recordset' https://www.postgresql.org/docs/14/functions-json.html
     * object T - the object which has a list
     *
     * @param whatReturn part of sql after 'select'. E - element of the list what return as result. Example: "to_jsonb(items)"
     * @param listWay part of sql after jsonb_to_recordset. Example: "'list'"; "'data' -> 'testsOverview'"
     * @param listDescription part of sql which describe the list of record. Example: "items("id" text)"
     * @param where part of sql with key 'where'. Example: "where name = 'test3'"
     */
    suspend inline fun <reified T : Any, reified E : Any> findInList(
        whatReturn: String,
        listWay: String,
        listDescription: String,
        where: String = "",
    ) = withContext(Dispatchers.IO) {
        executeInAsyncTransaction {
            try {
                val resultList = mutableListOf<E>()
                val tableName = T::class.createTableIfNotExists(connection.schema)
                execWrapper(
                    """
                            select $whatReturn 
                            FROM $tableName,
                                jsonb_to_recordset($tableName.json_body -> $listWay) as $listDescription
                            $where
                        """.trimIndent()
                ) {
                    while (it.next()) {
                        it.getString(1)?.let { jsonBody ->
                            resultList.add(
                                json.decodeFromString(
                                    E::class.serializer(),
                                    jsonBody
                                )
                            )
                        }
                    }
                }
                resultList
            } catch (e: Exception) {
                logger.warn { "cannot find cause exc: $e" }
                emptyList()
            }
        }
    }

    suspend inline fun <reified T : Any> findBy(noinline expression: Expr<T>.() -> Unit) =
        withContext<Collection<T>>(Dispatchers.IO) {
            executeInAsyncTransaction {
                findBy(expression)
            }
        }

    suspend inline fun <reified T : Any> deleteById(id: Any): Unit =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                deleteById<T>(id)
            }
        }


    suspend inline fun <reified T : Any> deleteBy(noinline expression: Expr<T>.() -> Unit) =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                deleteBy(expression)
            }
        }


    suspend inline fun <reified T : Any> deleteAll(): Unit =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                deleteAll<T>()
            }

        }
}

suspend inline fun <reified T : Any> Transaction.getAll(): MutableList<T> {
    val finalData = mutableListOf<T>()
    val tableName = T::class.createTableIfNotExists(connection.schema)
    execWrapper("select JSON_BODY FROM $tableName") { rs ->
        while (rs.next()) {
            finalData.add(
                json.decodeFromStream(
                    T::class.serializer(),
                    rs.getBinaryStream(1)
                )
            )
        }
    }
    return finalData
}

suspend inline fun <reified T : Any> Transaction.findBy(
    expression: Expr<T>.() -> Unit,
) = run {
    val tableName = T::class.createTableIfNotExists(connection.schema)
    val finalData = mutableListOf<T>()
    val sqlStatement = """
            |SELECT JSON_BODY FROM $tableName
            |WHERE ${Expr<T>().run { expression(this);conditions.joinToString(" ") }}
    """.trimMargin()
    execWrapper(sqlStatement) { rs ->
        while (rs.next()) {
            finalData.add(
                json.decodeFromStream(
                    T::class.serializer(),
                    rs.getBinaryStream(1)
                )
            )
        }
    }
    finalData
}

suspend inline fun <reified T : Any> Transaction.findById(
    id: Any,
): T? = run {
    var entity: T? = null
    val tableName = T::class.createTableIfNotExists(connection.schema)
    execWrapper("select JSON_BODY FROM $tableName WHERE ID='${id.hashCode()}'") { rs ->
        if (rs.next()) {
            entity = json.decodeFromStream(
                T::class.serializer(),
                rs.getBinaryStream(1)
            )
        }
    }
    entity
}

suspend inline fun <reified T : Any> Transaction.deleteById(
    id: Any,
) {
    val tableName = T::class.createTableIfNotExists(connection.schema)
    execWrapper("DELETE FROM $tableName WHERE ID='${id.hashCode()}'") //todo use parameters to detect type
}

suspend inline fun <reified T : Any> Transaction.deleteBy(
    noinline expression: Expr<T>.() -> Unit,
) {
    val tableName = T::class.createTableIfNotExists(connection.schema)
    execWrapper(
        """
                    |DELETE FROM $tableName
                    |WHERE ${Expr<T>().run { expression(this);conditions.joinToString(" ") }}
                    """.trimMargin()
    )
}

suspend inline fun <reified T : Any> Transaction.deleteAll() {
    val tableName = T::class.createTableIfNotExists(connection.schema)
    execWrapper("DELETE FROM $tableName")
}

suspend inline fun <reified T : Any> Transaction.store(any: T) {
    run {
        val schema = connection.schema
        val tableName = T::class.createTableIfNotExists(schema)
        val measureTime = measureTime {
            if (T::class.annotations.any { it is StreamSerialization }) {
                storeAsStream(any, tableName)
            } else {
                storeAsString(any, tableName)
            }
        }
        logger.trace { "Store object took: $measureTime" }
    }
}

inline fun <reified T : Any> Transaction.storeAsString(
    any: T,
    tableName: String,
) {
    val (_, idValue) = idPair(any)
    val stmt =
        """
            |INSERT INTO ${tableName.lowercase(Locale.getDefault())} (ID, JSON_BODY) VALUES ('${idValue.hashCode()}', CAST(? as jsonb))
            |ON CONFLICT (id) DO UPDATE SET JSON_BODY = excluded.JSON_BODY
        """.trimMargin()
    val stm = connection.prepareStatement(stmt, false)
    stm[1] = json.encodeToString(T::class.serializer(), any)
    stm.executeUpdate()
}

inline fun <reified T : Any> Transaction.storeAsStream(
    any: T,
    tableName: String,
) {
    val (_, idValue) = idPair(any)
    val file = File.createTempFile("prefix-", "-suffix") // TODO EPMDJ-9370 Remove file creating
    try {
        file.outputStream().use {
            json.encodeToStream(T::class.serializer(), any, it)
        }
        val stmt =
            """
            |INSERT INTO ${tableName.lowercase(Locale.getDefault())} (ID, JSON_BODY) VALUES ('${idValue.hashCode()}', CAST(? as jsonb))
            |ON CONFLICT (id) DO UPDATE SET JSON_BODY = excluded.JSON_BODY
        """.trimMargin()
        InputStreamReader(file.inputStream()).use {
            val prepareStatement = connection.prepareStatement(stmt, false) as JdbcPreparedStatementImpl
            prepareStatement.statement.setCharacterStream(1, it)
            prepareStatement.executeUpdate()
        }
    } finally {
        file.delete()
    }
}

val createdTables = mutableSetOf<String>()
val mutex = Mutex()

/**
 * Retrieving a table name from a given class, when the table doesn't exist, creates it.
 * By default, creates json table.
 */
suspend fun KClass<*>.createTableIfNotExists(
    schema: String,
    createTable: Transaction.(String) -> Unit = { tableName ->
        createJsonTable(tableName)
    },
): String {
    val tableName = camelRegex.replace(this.simpleName!!) {
        "_${it.value}"
    }.lowercase(Locale.getDefault())
    return createTableIfNotExists(schema, tableName, createTable)
}

suspend inline fun createTableIfNotExists(
    schema: String,
    tableName: String = "",
    noinline createTable: Transaction.(String) -> Unit,
): String {
    val tableKey = "$schema.$tableName"
    if (!createdTables.contains(tableKey)) {
        mutex.withLock {
            logger.trace { "check after lock $tableKey in $createdTables " }
            if (!createdTables.contains(tableKey)) {
                transaction {
                    createTable(tableName)
                }
                createdTables.add(tableKey)
            }
        }
    }
    return tableName
}
