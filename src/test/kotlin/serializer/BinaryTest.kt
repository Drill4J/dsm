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
package com.epam.dsm.serializer

import com.epam.dsm.*
import com.epam.dsm.common.*
import com.epam.dsm.test.*
import com.epam.dsm.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import org.jetbrains.exposed.sql.transactions.*
import kotlin.test.*

class BinaryTest : PostgresBased(schema) {

    companion object {
        private const val schema: String = "binary_test"
    }

    @Serializable
    data class BinaryClass(
        @Id val id: String,
        @Suppress("ArrayInDataClass")
        @Serializable(with = BinarySerializer::class)
        val bytes: ByteArray,
    )

    @Test
    fun `should store and retrieve binary data`() = runBlocking {
        val id = "someIDhere"
        val any = BinaryClass(id, byteArrayOf(1, 0, 1))
        storeClient.store(any)
        assertEquals(any.bytes.contentToString(), storeClient.findById<BinaryClass>(id)?.bytes?.contentToString())
    }

    @Test
    fun `should store and retrieve binary data steam`() {
        transaction {
            createBinaryTable()
        }
        val id = "id"
        val binary = byteArrayOf(-48, -94, -47, -117, 32, -48, -65, -48, -72, -48, -76, -48, -66, -47, -128, 33, 33)
        transaction {
            putBinary(id, binary)
        }
        val actual = transaction {
            getBinaryAsStream(id)
        }.readBytes()

        assertEquals(binary.contentToString(), actual.contentToString())
    }

    @Test
    fun `should store and retrieve binary data in two differ schema`() = runBlocking {
        val id = "someIDhere"
        val any = BinaryClass(id, byteArrayOf(1, 0, 1))
        storeClient.store(any)
        assertEquals(any.bytes.contentToString(), storeClient.findById<BinaryClass>(id)?.bytes?.contentToString())

        val newDbName = "newdb"
        val newDb = StoreClient(TestDatabaseContainer.createConfig(schema = newDbName))

        newDb.store(any)
        assertEquals(any.bytes.contentToString(), storeClient.findById<BinaryClass>(id)?.bytes?.contentToString())
        assertEquals(any.bytes.contentToString(), newDb.findById<BinaryClass>(id)?.bytes?.contentToString())

        storeClient.store(any.copy(id = "2"))

        assertEquals(2, storeClient.getAll<BinaryClass>().size)
        assertEquals(1, newDb.getAll<BinaryClass>().size)
    }

}
