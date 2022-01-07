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
@file:Suppress("NOTHING_TO_INLINE")

package com.epam.dsm.util

import com.epam.dsm.*
import com.github.luben.zstd.*
import mu.*
import org.jetbrains.exposed.sql.*
import java.io.*
import java.sql.*
import java.util.*
import kotlin.reflect.*

val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()

val logger = KotlinLogging.logger {}

val dbContext = InheritableThreadLocal<String>()

//TODO EPMDJ-9213 get schema from TransactionManagement or connection
fun currentSchema() = dbContext.get() ?: throw RuntimeException("Cannot find schema. Lost db context")


fun Transaction.createJsonTable(schema: String, simpleName: String) {
    execWrapper("CREATE TABLE IF NOT EXISTS $schema.$simpleName (ID varchar(256) not null constraint ${simpleName}_pk primary key, JSON_BODY jsonb);")
    commit()
}

fun Transaction.createBinaryTable(schema: String) {
    execWrapper(
        """
             CREATE TABLE IF NOT EXISTS $schema.BINARYA (ID varchar(256) not null constraint binarya_pk primary key, binarya bytea);
             ALTER TABLE $schema.BINARYA ALTER COLUMN binarya SET STORAGE EXTERNAL; 
             """.trimIndent()
    )
    commit()
}

fun Transaction.putBinary(schema: String, id: String, value: ByteArray) {
    val prepareStatement = connection.prepareStatement("INSERT INTO $schema.BINARYA VALUES ('$id', ?)", false)
    prepareStatement[1] = Zstd.compress(value)
    prepareStatement.executeUpdate()
}

fun Transaction.getBinary(schema: String, id: String): ByteArray {
    val prepareStatement = connection.prepareStatement(
        "SELECT BINARYA FROM $schema.BINARYA " +
                "WHERE id = '$id'", false
    )
    val executeQuery = prepareStatement.executeQuery()
    return if (executeQuery.next()) {
        val bytes = executeQuery.getBytes(1)
        Zstd.decompress(bytes, Zstd.decompressedSize(bytes).toInt())
    } else byteArrayOf() //todo or throw error?
}

fun Transaction.getBinaryAsStream(schema: String, id: String): InputStream {
    val prepareStatement = connection.prepareStatement(
        "SELECT BINARYA FROM $schema.BINARYA " +
                "WHERE id = '$id'", false
    )
    val executeQuery = prepareStatement.executeQuery()
    return if (executeQuery.next())
        ZstdInputStream(executeQuery.getBinaryStream(1))
    else ByteArrayInputStream(ByteArray(0)) //todo or throw error?

}

inline fun Transaction.execWrapper(
    sqlStatement: String,
    args: Iterable<Pair<IColumnType, Any?>> = emptyList(),
    noinline transform: (ResultSet) -> Unit = {},
) {
    logger.trace { "SQL statement: $sqlStatement" }
    exec(sqlStatement, args, transform = transform)
}
