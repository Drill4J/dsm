package com.epam.dsm

import com.epam.dsm.serializer.BinarySerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import com.epam.dsm.serializer.BitSetSerializer
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class BinaryTest : PostgresBased() {

    private val schema = "binarytest"
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
    data class BinaryClass(
        @Id
        val id: String,
        @Suppress("ArrayInDataClass")
        @Serializable(with = BinarySerializer::class)
        val bytes: ByteArray
    )


    @Test
    fun shouldStoreAndRetrieveBinaryData() = runBlocking<Unit> {
        agentStore.store(
            BinaryClass(
                "someIDhere",
                byteArrayOf()
            )
        )
    }
}