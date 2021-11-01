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
    fun shouldStoreBitSetInSeparateTable() = runBlocking<Unit> {
        val size = 10
        val btst = BitSet(size)
            .apply {
                set(8)
                set(6)
                set(4)
                set(2)
                set(0)
            }
        agentStore.store(
            BitsetClass(
                "someIDhere",
                btst
            )
        )
        assertEquals(btst, agentStore.getAll<BitsetClass>().first().btst)
    }
}

fun BitSet(nbits: Int): BitSet {
    return java.util.BitSet(nbits + 1)
        .apply { set(10) } //bitsetMagic
}