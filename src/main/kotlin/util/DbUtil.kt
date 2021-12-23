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

val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()

val logger = KotlinLogging.logger {}

fun Transaction.createJsonTable(tableName: String) {
    execWrapper("""
        CREATE TABLE IF NOT EXISTS $tableName (
            $ID_COLUMN varchar(256) not null constraint ${tableName}_pk primary key, 
            $PARENT_ID_COLUMN varchar(256), 
            $JSON_COLUMN jsonb
        );
        """)
    commit()
}

fun Transaction.createBinaryTable() {
    execWrapper(
        """
             CREATE TABLE IF NOT EXISTS BINARYA ($ID_COLUMN varchar(256) not null constraint binarya_pk primary key, binarya bytea);
             ALTER TABLE BINARYA ALTER COLUMN binarya SET STORAGE EXTERNAL; 
             """.trimIndent()
    )
    commit()
}

fun Transaction.putBinary(id: String, value: ByteArray) {
    val prepareStatement = connection.prepareStatement("INSERT INTO BINARYA VALUES ('$id', ?)", false)
    prepareStatement[1] = Zstd.compress(value)
    prepareStatement.executeUpdate()
}

fun Transaction.getBinary(id: String): ByteArray {
    val prepareStatement = connection.prepareStatement(
        "SELECT BINARYA FROM BINARYA WHERE $ID_COLUMN = ${id.toQuotes()}",
        false
    )
    val executeQuery = prepareStatement.executeQuery()
    return if (executeQuery.next()) {
        val bytes = executeQuery.getBytes(1)
        Zstd.decompress(bytes, Zstd.decompressedSize(bytes).toInt())
    } else byteArrayOf() //todo or throw error?
}

fun Transaction.getBinaryAsStream(id: String): InputStream {
    val prepareStatement = connection.prepareStatement(
        "SELECT BINARYA FROM BINARYA " +
                "WHERE $ID_COLUMN = ${id.toQuotes()}", false
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
    logger.trace { "SQL statement on schema '${connection.schema}': $sqlStatement" }
    exec(sqlStatement, args, transform = transform)
}
