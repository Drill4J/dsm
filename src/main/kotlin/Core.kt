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

class StoreClient(val schema: String) {
    init {
        transaction {
            execWrapper("CREATE SCHEMA IF NOT EXISTS $schema")
        }
    }

    suspend fun <T> executeInAsyncTransaction(block: suspend Transaction.() -> T) = withContext(Dispatchers.IO) {
        newSuspendedTransaction {
            block(this)
        }
    }

    suspend inline fun <reified T : Any> store(any: T) = run {
        executeInAsyncTransaction {
            store(any, schema)
        }
        any
    }

    suspend inline fun <reified T : Any> getAll(): Collection<T> =
        executeInAsyncTransaction {
            getAll(schema)
        }

    suspend inline fun <reified T : Any> findById(id: Any): T? =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                findById(schema, id)
            }
        }


    suspend inline fun <reified T : Any> findBy(noinline expression: Expr<T>.() -> Unit) =
        withContext<Collection<T>>(Dispatchers.IO) {
            executeInAsyncTransaction {
                findBy(schema, expression)
            }
        }

    suspend inline fun <reified T : Any> deleteById(id: Any): Unit =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                deleteById<T>(schema, id)
            }
        }


    suspend inline fun <reified T : Any> deleteBy(noinline expression: Expr<T>.() -> Unit) =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                deleteBy(schema, expression)
            }
        }


    suspend inline fun <reified T : Any> deleteAll(): Unit =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                deleteAll<T>(schema)
            }

        }
}

suspend inline fun <reified T : Any> Transaction.getAll(schema: String): MutableList<T> {
    val finalData = mutableListOf<T>()
    try {
        dbContext.set(schema)
        val tableName = T::class.createTableIfNotExists(schema)
        execWrapper("select JSON_BODY FROM $schema.$tableName") { rs ->
            while (rs.next()) {
                finalData.add(
                    json.decodeFromStream(
                        T::class.serializer(),
                        rs.getBinaryStream(1)
                    )
                )
            }
        }
    } finally {
        dbContext.remove()
    }
    return finalData
}

suspend inline fun <reified T : Any> Transaction.findBy(
    schema: String,
    expression: Expr<T>.() -> Unit,
) = try {
    dbContext.set(schema)
    val tableName = T::class.createTableIfNotExists(schema)
    val finalData = mutableListOf<T>()
    val sqlStatement = """
            |SELECT JSON_BODY FROM $schema.$tableName
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
} finally {
    dbContext.remove()
}

suspend inline fun <reified T : Any> Transaction.findById(
    schema: String,
    id: Any,
): T? {
    try {
        dbContext.set(schema)
        var entity: T? = null
        val tableName = T::class.createTableIfNotExists(schema)
        execWrapper("select JSON_BODY FROM $schema.$tableName WHERE ID='${id.hashCode()}'") { rs ->
            if (rs.next()) {
                entity = json.decodeFromStream(
                    T::class.serializer(),
                    rs.getBinaryStream(1)
                )
            }
        }
        return entity
    } finally {
        dbContext.remove()
    }
}

suspend inline fun <reified T : Any> Transaction.deleteById(
    schema: String,
    id: Any,
) {
    val tableName = T::class.createTableIfNotExists(schema)
    execWrapper("DELETE FROM $schema.$tableName WHERE ID='${id.hashCode()}'") //todo use parameters to detect type
}

suspend inline fun <reified T : Any> Transaction.deleteBy(
    schema: String,
    noinline expression: Expr<T>.() -> Unit,
) {
    val tableName = T::class.createTableIfNotExists(schema)
    execWrapper(
        """
                    |DELETE FROM $schema.$tableName
                    |WHERE ${Expr<T>().run { expression(this);conditions.joinToString(" ") }}
                    """.trimMargin()
    )
}

suspend inline fun <reified T : Any> Transaction.deleteAll(
    schema: String,
) {
    val tableName = T::class.createTableIfNotExists(schema)
    execWrapper("DELETE FROM $schema.$tableName")
}

suspend inline fun <reified T : Any> Transaction.store(any: T, schema: String) {
    try {
        val tableName = T::class.createTableIfNotExists(schema)
        dbContext.set(schema)
        val measureTime = measureTime {
            if (T::class.annotations.any { it is StreamSerialization }) {
                storeAsStream(any, schema, tableName)
            } else {
                storeAsString(any, schema, tableName)
            }
        }
        logger.debug { "Store object took: $measureTime" }
    } finally {
        dbContext.remove()
    }
}

inline fun <reified T : Any> Transaction.storeAsString(
    any: T,
    schema: String,
    tableName: String,
) {
    val (_, idValue) = idPair(any)
    val json = json.encodeToString(T::class.serializer(), any)
    val stmt =
        """
            |INSERT INTO $schema.${tableName.lowercase(Locale.getDefault())} (ID, JSON_BODY) VALUES ('${idValue.hashCode()}', '$json')
            |ON CONFLICT (id) DO UPDATE SET JSON_BODY = excluded.JSON_BODY
        """.trimMargin()
    execWrapper(stmt)
}

inline fun <reified T : Any> Transaction.storeAsStream(
    any: T,
    schema: String,
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
            |INSERT INTO $schema.${tableName.lowercase(Locale.getDefault())} (ID, JSON_BODY) VALUES ('${idValue.hashCode()}', CAST(? as jsonb))
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
    createTable: Transaction.(String, String) -> Unit = { sch, tableName ->
        createJsonTable(sch, tableName)
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
    noinline createTable: Transaction.(String, String) -> Unit,
): String {
    val tableKey = "$schema.$tableName"
    if (!createdTables.contains(tableKey)) {
        mutex.withLock {
            logger.trace { "check after lock $tableKey in $createdTables " }
            if (!createdTables.contains(tableKey)) {
                transaction {
                    createTable(schema, tableName)
                }
                createdTables.add(tableKey)
            }
        }
    }
    return tableName
}
