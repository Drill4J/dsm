/**
 * Copyright 2020 EPAM Systems
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
@file:Suppress("CovariantEquals")

package com.epam.dsm

import com.epam.dsm.util.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.transactions.experimental.*
import java.sql.*
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

    suspend inline fun <reified T : Any> store(any: T): T {
        executeInAsyncTransaction {
            store(any, schema)
        }
        return any
    }

    suspend inline fun <reified T : Any> getAll(): Collection<T> =
        executeInAsyncTransaction {
            val finalData = mutableListOf<T>()
            try {
                dbContext.set(schema)
                val simpleName = T::class.toTableName()

                execWrapper("select JSON_BODY FROM $schema.$simpleName") { rs ->
                    while (rs.next()) {
                        val jsonBody = rs.getString(1)
                        finalData.add(
                            json.decodeFromString(
                                T::class.serializer(),
                                jsonBody
                            )
                        )
                    }
                }


            } catch (ex: Exception) {
                //todo?
            } finally {
                dbContext.remove()
            }
            finalData
        }

    suspend inline fun <reified T : Any> findById(id: Any): T? =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                try {
                    dbContext.set(schema)
                    var rs: T? = null
                    val simpleName = T::class.toTableName()
                    execWrapper("select JSON_BODY FROM $schema.$simpleName WHERE ID='${id.hashCode()}'") {
                        if (it.next()) {
                            rs = json.decodeFromString(
                                T::class.serializer(),
                                it.getString(1)
                            )
                            return@execWrapper
                        }
                    }
                    rs
                } catch (e: Exception) {
                    //todo
                    null
                } finally {
                    dbContext.remove()
                }
            }
        }


    suspend inline fun <reified T : Any> findBy(noinline expression: Expr<T>.() -> Unit) =
        withContext<Collection<T>>(Dispatchers.IO) {
            executeInAsyncTransaction {
                try {
                    dbContext.set(schema)
                    val simpleName = T::class.toTableName()
                    val finalData = mutableListOf<T>()
                    val transform: (ResultSet) -> Unit = { rs ->
                        while (rs.next()) {
                            val jsonBody = rs.getString(1)
                            finalData.add(
                                json.decodeFromString(
                                    T::class.serializer(),
                                    jsonBody
                                )
                            )
                        }
                    }
                    val sqlStatement = """
                                   |select JSON_BODY FROM $schema.$simpleName
                                   |WHERE ${Expr<T>().run { expression(this);conditions.joinToString(" ") }}
                               """.trimMargin()
                    execWrapper(sqlStatement, transform = transform)
                    finalData
                } catch (e: Exception) {
                    //todo
                    emptyList()
                } finally {
                    dbContext.remove()
                }
            }
        }

    suspend inline fun <reified T : Any> deleteById(id: Any): Unit =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                val simpleName = T::class.toTableName()
                execWrapper("DELETE FROM $schema.$simpleName WHERE ID='${id.hashCode()}'") //todo use parameters to detect type
            }
        }


    suspend inline fun <reified T : Any> deleteBy(noinline expression: Expr<T>.() -> Unit) =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                val simpleName = T::class.toTableName()
                execWrapper(
                    """
                    |DELETE FROM $schema.$simpleName
                    |WHERE ${Expr<T>().run { expression(this);conditions.joinToString(" ") }}
                    """.trimMargin()
                )
            }
        }


    suspend inline fun <reified T : Any> deleteAll(): Unit =
        withContext(Dispatchers.IO) {
            executeInAsyncTransaction {
                val simpleName = T::class.toTableName()
                execWrapper("DELETE FROM $schema.$simpleName")
            }

        }
}

val atomicState = AtomicState()

suspend inline fun <reified T : Any> Transaction.store(any: T, schema: String) {
    try {
        dbContext.set(schema)
        val (_, idValue) = idPair(any)
        val simpleName = T::class.toTableName()
        val tableKey = "$schema:$simpleName"
        if (atomicState.isNeedCreateTable(tableKey)) {
            transaction {
                createJsonTable(schema, simpleName)
                commit()
            }
            atomicState.update(tableKey, AtomicState.TableState.CREATED)
        }

        val json = json.encodeToString(T::class.serializer(), any)
        val stmt =
            """
            |INSERT INTO $schema.${simpleName.toLowerCase()} (ID, JSON_BODY) VALUES ('${idValue.hashCode()}', '$json')
            |ON CONFLICT (id) DO UPDATE SET JSON_BODY = excluded.JSON_BODY
        """.trimMargin()
        val measureTime = measureTime {
            waitUntilTableCreate(tableKey)
            execWrapper(stmt)
        }
        logger.debug { "Store object took: $measureTime" }
    } finally {
        dbContext.remove()
    }
}

suspend fun waitUntilTableCreate(key: String) {
    while (true) {
        if (!atomicState.isCreatedTable(key)) {
            delay(10)
        } else break
    }
}


//todo class is hack for atomic. After update version it will able to remove
class AtomicState {
    private val mutex = Mutex()
    private val tableStates = atomic(persistentHashMapOf<String, TableState>())

    enum class TableState {
        CREATING,
        CREATED
    }

    suspend fun isNeedCreateTable(key: String): Boolean {
        logger.trace { "isCreatingFirst $key" }
        if (tableStates.value[key] == null) {//todo is it faster?
            mutex.withLock {
                if (tableStates.value[key] == null) {
                    logger.trace { "table '$key' is creating..."}
                    tableStates.update {
                        it.put(key, TableState.CREATING)
                    }
                    return true
                }
            }
        }
        return false
    }

    fun update(key: String, tableState: TableState) {
        logger.trace { "update $key state $tableState" }
        tableStates.update {
            it.put(key, tableState)
        }
    }

    fun isCreatedTable(key: String): Boolean = tableStates.value[key] == TableState.CREATED

    fun clear() {
        tableStates.update { it.clear() }
    }

}

