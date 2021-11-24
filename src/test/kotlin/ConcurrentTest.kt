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

import kotlinx.coroutines.*
import kotlin.test.*

class ConcurrentTest : PostgresBased("concurrent_test") {
    private val simpleObject = SimpleObject("id", "subStr", 12, Last(2.toByte()))

    @Test
    fun `should make queries sequence`() = runBlocking {
        repeat(100) {
            println("$it starting")
            val simpleObject = agentStore.findById<SimpleObject>("id$it")
            assertNull(simpleObject)
            println("$it finished")
        }
    }

    @Test
    fun `should parallel find`() = runBlocking {
        repeat(10) {
            launch(Dispatchers.Default) {
                agentStore.findById<SimpleObject>("id$it")
            }
        }
    }

    @Test
    fun `should store sequence`() = runBlocking {
        repeat(100) {
            println("store $it")
            agentStore.store(simpleObject.copy(id = "agent$it"))
            println("$it finished")
        }
        println("finished")
        assertEquals(100, agentStore.getAll<SimpleObject>().size)
    }

    @Test
    fun `should parallel store`() = runBlocking {
        val list = mutableListOf<Job>()
        val times = 2
        repeat(times) {
            val job = launch(Dispatchers.Default) {
                println("store $it")
                agentStore.store(simpleObject.copy(id = "agent$it"))
                println("finished $it")
            }
            list.add(job)
        }
        joinAll(*list.toTypedArray())
        assertEquals(times, agentStore.getAll<SimpleObject>().size)
    }

    @Test
    fun `should parallel store in one transaction`() = runBlocking {
        val list = mutableListOf<Job>()
        val times = 2
        repeat(times) {
            val job = launch(Dispatchers.Default) {
                println("storing $it...")
                agentStore.executeInAsyncTransaction {
                    store(simpleObject.copy(id = "agent$it"), agentStore.schema)
                }
                println("finished $it")
            }
            list.add(job)
        }
        joinAll(*list.toTypedArray())
        assertEquals(times, agentStore.getAll<SimpleObject>().size)
    }

}