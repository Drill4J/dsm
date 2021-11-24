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
package com.epam.dsm

import com.epam.dsm.serializer.*
import com.epam.dsm.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import org.jetbrains.exposed.sql.transactions.*
import org.junit.jupiter.api.*
import kotlin.test.*
import kotlin.test.Test

class BinaryTest : PostgresBased(schema) {

    companion object {
        private const val schema: String = "binary_test"
    }

    @Serializable
    data class BinaryClass(
        @Id
        val id: String,
        @Suppress("ArrayInDataClass")
        @Serializable(with = BinarySerializer::class)
        val bytes: ByteArray,
    )

    @Test
    fun `should store and retrieve binary data`() = runBlocking {
        val id = "someIDhere"
        val any = BinaryClass(id, byteArrayOf(1, 0, 1))
        agentStore.store(any)
        assertEquals(any.bytes.contentToString(), agentStore.findById<BinaryClass>(id)?.bytes?.contentToString())
    }

    @Test
    fun `should store and retrieve binary data steam`() {
        transaction {
            createBinaryTable(schema)
        }
        val id = "id"
        val binary = byteArrayOf(-48, -94, -47, -117, 32, -48, -65, -48, -72, -48, -76, -48, -66, -47, -128, 33, 33)
        transaction {
            putBinary(schema, id, binary)
        }
        val actual = transaction {
            getBinaryAsStream(schema, "id")
        }.readBytes()

        assertEquals(binary.contentToString(), actual.contentToString())
    }

}
