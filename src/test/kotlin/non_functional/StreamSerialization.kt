package com.epam.dsm.non_functional

import com.epam.dsm.*
import com.epam.dsm.common.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*

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
class StreamSerializationTest : PostgresBased("stream_serialization") {
    val size = 100_000
    private val annotatedLargeObject = LargeObjectWithStreamSerializationAnnotation(
        "id",
        (1..size).map { Data("$it".repeat(20), "$it".repeat(20), "$it".repeat(20)) },
        (1..size).associate {
            "$it".repeat(20) to Data(
                "$it".repeat(20),
                "$it".repeat(20),
                "$it".repeat(20)
            )
        }
    )
    private val largeObject: LargeObject = LargeObject(
        "id",
        annotatedLargeObject.list,
        annotatedLargeObject.map
    )

    @Test
    fun `should store object with inner lists`(): Unit = runBlocking {
        measureTime { storeClient.store(annotatedLargeObject) }.also {
            println("[Store large object] took $it")
        }
        val stored = storeClient.findById<LargeObjectWithStreamSerializationAnnotation>("id")
        assertNotNull(stored)
        assertTrue { stored.list.any() }
        assertTrue { stored.map.any() }
        assertTrue { stored.list.containsAll(annotatedLargeObject.list) }
        annotatedLargeObject.map.forEach { (key, value) ->
            val storedValue = stored.map[key]
            assertNotNull(storedValue)
            assertTrue { storedValue == value }
        }
    }

    /**
     *  OOM With such config with `string memory test` and all ok with `stream memory test`
     *  Tested with heap size = 1g
     */
    @Test
    @Ignore
    fun `save object as string memory test`() = runBlocking {
        storeClient.store(largeObject)
        val largeObjectFromDB = storeClient.findById<LargeObject>("id")
        assertEquals(largeObjectFromDB, largeObjectFromDB)
    }
}
