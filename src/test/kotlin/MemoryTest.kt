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

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 *  OOM With such config with `string memory test` and all ok with `stream memory test`
 *  Tested with heap size = 1g
 */
@Disabled
class MemoryTest : PostgresBased(schema) {

    private val size = 200_000
    private val largeObject = LargeObject(
        "id",
        (1..size).map { "$it".repeat(20) },
        (1..size).associate { "$it".repeat(20) to "$it".repeat(20) },
    )

    private val annotatedLargeObject = LargeObjectWithStreamSerializationAnnotation(
        "id",
        (1..size).map { "$it".repeat(20) },
        (1..size).associate { "$it".repeat(20) to "$it".repeat(20) },
    )

    companion object {
        private const val schema = "stream"
    }

    @Test()
    fun `save object using stream memory test`(): Unit = runBlocking {
        agentStore.store(annotatedLargeObject)
        val largeObjectFromDB = agentStore.findById<LargeObjectWithStreamSerializationAnnotation>("id")
        assertEquals(annotatedLargeObject, largeObjectFromDB)
    }

    @Test
    fun `save object as string memory test`() = runBlocking {
        agentStore.store(largeObject)
        val largeObjectFromDB = agentStore.findById<LargeObject>("id")
        assertEquals(largeObjectFromDB, largeObjectFromDB)
    }
}

