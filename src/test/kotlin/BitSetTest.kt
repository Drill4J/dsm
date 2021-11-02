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

import com.epam.dsm.serializer.BitSetSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BitSetTest : PostgresBased("bitset") {

    @Serializable
    data class BitsetClass(
        @Id
        val id: String,
        @Serializable(with = BitSetSerializer::class)
        val btst: BitSet
    )

    @Test
    fun shouldStoreBitSetInSeparateTable() = runBlocking {
        val size = 5_000
        val btst = BitSet(size)
            .apply {
                set(8)
                set(6)
                set(4)
                set(2)
                set(0)
            }
        val id = "someIDhere"
        agentStore.store(
            BitsetClass(
                id,
                btst
            )
        )
        assertEquals(btst, agentStore.findById<BitsetClass>(id)?.btst)
    }
}

fun BitSet(nbits: Int): BitSet {
    return java.util.BitSet(nbits + 1)
        .apply { set(nbits) } //bitsetMagic
}