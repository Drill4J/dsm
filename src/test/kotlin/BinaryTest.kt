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

import com.epam.dsm.serializer.BinarySerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class BinaryTest : PostgresBased("binary_test") {

    @Serializable
    data class BinaryClass(
        @Id
        val id: String,
        @Suppress("ArrayInDataClass")
        @Serializable(with = BinarySerializer::class)
        val bytes: ByteArray
    )


    @Test
    fun shouldStoreAndRetrieveBinaryData() = runBlocking {
        val id = "someIDhere"
        val any = BinaryClass(id, byteArrayOf(1, 0, 1))
        agentStore.store(any)
        assertEquals(any.bytes.contentToString(), agentStore.findById<BinaryClass>(id)?.bytes?.contentToString())
    }
}
