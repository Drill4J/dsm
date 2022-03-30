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
package com.epam.dsm

import com.epam.dsm.serializer.*
import com.epam.dsm.util.*
import com.zaxxer.hikari.pool.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.*
import java.io.*
import java.util.*
import kotlin.reflect.*

typealias EntrySize = Pair<Int, Int>
typealias EntryClass<T, R> = Pair<KClass<T>, KClass<R>>
typealias EntrySerializer<T, R> = Pair<KSerializer<T>, KSerializer<R>>

// As per https://youtrack.jetbrains.com/issue/KT-10440 there isn't a reliable way to get back a KClass for a
// Kotlin primitive types
val PRIMITIVE_CLASSES = mapOf<SerialKind, KClass<*>>(
    PrimitiveKind.BOOLEAN to Boolean::class,
    PrimitiveKind.BYTE to Byte::class,
    PrimitiveKind.CHAR to Char::class,
    PrimitiveKind.FLOAT to Float::class,
    PrimitiveKind.DOUBLE to Double::class,
    PrimitiveKind.INT to Int::class,
    PrimitiveKind.LONG to Long::class,
    PrimitiveKind.SHORT to Short::class,
    PrimitiveKind.STRING to String::class,
)

fun Transaction.createMapTable(tableName: String) {
    execWrapper("CREATE TABLE IF NOT EXISTS $tableName (ID varchar(256) not null constraint ${tableName}_pk primary key, PARENT_ID varchar(256) not null, KEY_JSON jsonb, VALUE_JSON jsonb);")
    commit()
}

/**
 * File is needed to don't keep a huge map in memory
 */
fun <T : Any?, R : Any?> storeMap(
    map: Map<T, R>,
    parentId: Int?,
    parentIndex: Int?,
    entryClass: EntryClass<*, *>,
    serializer: EntrySerializer<Any, Any>,
): Unit = transaction {
    if (map.none()) return@transaction
    val tableName = runBlocking {
        createTableIfNotExists<Any>(connection.schema, entryClass.tableName()) {
            createMapTable(it)
        }
    }
    val file = File.createTempFile("prefix-", "-postfix") // TODO EPMDJ-9370 Remove file creating
    val sizes = mutableListOf<EntrySize>()
    try {
        file.outputStream().use {
            map.forEach { (key, value) ->
                if (key != null && value != null) {
                    val sizeBefore = it.size()
                    json.encodeToStream(serializer.first, key, it)
                    val keySize = (it.size() - sizeBefore).toInt()
                    json.encodeToStream(serializer.second, value, it)
                    val valueSize = (it.size() - keySize - sizeBefore).toInt()
                    sizes.add(keySize to valueSize)
                }
            }
            sizes
        }
        val stmt = """
            |INSERT INTO ${tableName.lowercase(Locale.getDefault())} (ID, PARENT_ID, KEY_JSON, VALUE_JSON) VALUES (?,'$parentId', CAST(? as jsonb), CAST(? as jsonb))
            |ON CONFLICT (id) DO UPDATE SET PARENT_ID = excluded.PARENT_ID, KEY_JSON = excluded.KEY_JSON, VALUE_JSON = excluded.VALUE_JSON
        """.trimMargin()
        val statement = (connection.connection as HikariProxyConnection).prepareStatement(stmt)
        file.inputStream().reader().use {
            sizes.forEachIndexed { index, (keySize, valueSize) ->
                statement.setString(1, elementId(parentId, parentIndex, index))
                statement.setCharacterStream(2, it, keySize)
                statement.setCharacterStream(3, it, valueSize)
                statement.addBatch()
                if (index % DSM_PUSH_LIMIT == 0) {
                    statement.executeBatch()
                    statement.clearBatch()
                }
            }
            statement.executeBatch()
        }
    } finally {
        file.delete()
    }
}

/**
 * Loading of map by regular expression: by prefix which is parentId plus parentIndex
 */
inline fun <reified T : Any, reified R : Any> loadMap(
    id: String,
    clazz: EntryClass<T, R>,
    serializer: EntrySerializer<T, R>,
): Map<T, R> = transaction {
    val entities: MutableMap<T, R> = mutableMapOf()
    if (id.isBlank()) return@transaction entities
    val tableName = clazz.tableName()
    runBlocking {
        createTableIfNotExists<Any>(connection.schema, tableName) {
            createMapTable(tableName)
        }
    }
    val stm = "select KEY_JSON, VALUE_JSON FROM $tableName WHERE ID ~ '${id}'"
    val statement = (connection.connection as HikariProxyConnection).prepareStatement(stm)
    statement.fetchSize = DSM_FETCH_LIMIT
    statement.executeQuery().let { rs ->
        while (rs.next()) {
            val key = json.decodeFromStream(serializer.first, rs.getBinaryStream(1))
            val value = json.decodeFromStream(serializer.second, rs.getBinaryStream(2))
            entities[key] = value
        }
    }
    return@transaction entities
}

@Suppress("UNCHECKED_CAST")
fun ClassLoader.getClass(descriptor: SerialDescriptor): KClass<Any> = when (val kind = descriptor.kind) {
    is PrimitiveKind -> PRIMITIVE_CLASSES[kind]
    is StructureKind.LIST -> List::class
    is StructureKind.MAP -> Map::class
    else -> loadClass(descriptor.serialName).kotlin
} as KClass<Any>