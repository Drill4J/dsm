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
package com.epam.drill

import com.epam.dsm.*
import com.epam.dsm.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.openjdk.jmh.annotations.*
import java.util.concurrent.*
import kotlin.random.*
import kotlin.test.*


@State(Scope.Benchmark)
@Fork
@Warmup(iterations = 3)
@Measurement(iterations = 5, timeUnit = TimeUnit.MILLISECONDS)
class StoreMap : Configuration() {

    private val schema = "map_perf_test"
    private val client = StoreClient(TestDatabaseContainer.createDataSource(schema = schema))

    private val testValue = ObjectWithMap(
        "id",
        (1..500).associate { "$it" to ObjectWithList((1..500).map { Data() }) }
    )

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(Threads.MAX)
    fun storeMapInNewTable(): Unit = runBlocking {
        client.store(testValue)
        val stored = client.findById<ObjectWithMap>(testValue.id)
        assertNotNull(stored)
    }


}

@Serializable
data class ObjectWithMap(
    @Id val id: String,
    val map: Map<String, ObjectWithList>,
)

@Serializable
data class ObjectWithList(
    val list: List<Data>,
)

@Serializable
data class Data(
    val count: Int = Random.nextInt(),
    val string: String = "${Random.nextInt()}",
)
