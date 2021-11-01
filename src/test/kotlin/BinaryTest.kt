package com.epam.dsm

import com.epam.dsm.serializer.BinarySerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test

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
    fun shouldStoreAndRetrieveBinaryData() = runBlocking<Unit> {
        agentStore.store(
            BinaryClass(
                "someIDhere",
                byteArrayOf()
            )
        )
    }
}