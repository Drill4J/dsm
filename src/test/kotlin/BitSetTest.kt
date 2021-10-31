package com.epam.dsm

import com.epam.dsm.util.stringRepresentation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import serializer.BitSetSerializer
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class BitSetTest {

    private val schema = "bittest"
    private val agentStore = StoreClient(schema)

    @AfterTest
    fun after() {
        transaction {
            exec("DROP SCHEMA $schema CASCADE")
        }
    }

    @BeforeTest
    fun before() {
        transaction {
            exec("CREATE SCHEMA IF NOT EXISTS $schema")
        }
    }

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
        agentStore.store(
            BitsetClass(
                "someIDhere",
                BitSet(size + 1)
                    .apply { set(10) } //bitsetMagic
                    .apply {
                        set(8)
                        set(6)
                        set(4)
                        set(2)
                        set(0)
                    }
            )
        )
    }
}