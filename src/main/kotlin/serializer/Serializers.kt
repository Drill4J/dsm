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
package com.epam.dsm.serializer

import com.epam.dsm.*
import com.epam.dsm.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.transactions.*
import java.io.*
import java.util.*
import kotlin.reflect.*

val DSM_FETCH_AND_PUSH_LIMIT = System.getenv("DSM_FETCH_AND_PUSH_LIMIT")?.toIntOrNull() ?: 10_000

object BinarySerializer : KSerializer<ByteArray> {

    override fun serialize(encoder: Encoder, value: ByteArray) {
        val id = UUID.randomUUID().toString()
        transaction {
            val schema = connection.schema
            runBlocking {
                createTableIfNotExists(schema) {
                    createBinaryTable()
                }
            }
            logger.trace { "serialize for id '$id' in schema $schema" }
            putBinary(id, value)
        }
        encoder.encodeSerializableValue(String.serializer(), id)
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val id = decoder.decodeSerializableValue(String.serializer())
        return transaction { getBinary(id) }
    }

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Binary")
}

/**
 * This serializer stores Collections, in the future Map and ByteArray in a separate table
 * Hashcode of parent object field with annotation @Id is used as the id.
 *
 * Custom serialization for BitSet when storing: Bitsets are serialized into a string of 0's and 1's.
 */
class DsmSerializer<T>(
    private val serializer: KSerializer<T>,
    val classLoader: ClassLoader,
    val parentId: String? = null,
) : KSerializer<T> by serializer {
    override fun serialize(encoder: Encoder, value: T) {
        if (serializer.isBitSet()) {
            encoder.encodeSerializableValue(String.serializer(), (value as BitSet).stringRepresentation())
        } else {
            serializer.serialize(DsmEncoder(encoder), value)
        }
    }

    override fun deserialize(decoder: Decoder): T {
        if (serializer.isBitSet()) {
            val decodeSerializableValue = decoder.decodeSerializableValue(String.serializer())
            @Suppress("UNCHECKED_CAST")
            return decodeSerializableValue.toBitSet() as T
        }
        return serializer.deserialize(DsmDecoder(decoder))
    }

    inner class DsmEncoder(private val encoder: Encoder) : Encoder by encoder {

        override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
            val compositeEncoder = encoder.beginStructure(descriptor)
            return DsmCompositeEncoder(compositeEncoder)
        }

        inner class DsmCompositeEncoder(
            private val compositeEncoder: CompositeEncoder,
        ) : CompositeEncoder by compositeEncoder {

            override fun <T> encodeSerializableElement(
                descriptor: SerialDescriptor,
                index: Int,
                serializer: SerializationStrategy<T>,
                value: T,
            ) {
                when (value) {
                    is Map<*, *>, is ByteArray, is Enum<*> -> {
                        //TODO EPMDJ-9884 Optimize map storing
                        //TODO EPMDJ-9885 Get rid of the ByteArraySerializer
                        compositeEncoder.encodeSerializableElement(descriptor, index, serializer, value)
                    }
                    is Collection<*> -> {
                        val elementDescriptor = serializer.descriptor.getElementDescriptor(0)
                        if (!value.isEmpty() && elementDescriptor.kind !is PrimitiveKind) {
                            val clazz = getClass(elementDescriptor, classLoader)

                            val elementSerializer = clazz.dsmSerializer(parentId, classLoader)
                            storeCollection(value.filterNotNull(), parentId, clazz, elementSerializer)
                            compositeEncoder.encodeSerializableElement(
                                descriptor,
                                index,
                                ListSerializer(String.serializer()),
                                value.mapIndexed { i, _ -> elementId(i, parentId) }
                            )
                        } else compositeEncoder.encodeSerializableElement(descriptor, index, serializer, value)
                    }
                    else -> {
                        val strategy = DsmSerializer(serializer as KSerializer<T>, classLoader, parentId)
                        compositeEncoder.encodeSerializableElement(descriptor, index, strategy, value)
                    }
                }
            }
        }
    }

    inner class DsmDecoder(private val decoder: Decoder) : Decoder by decoder {

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
            return DsmCompositeDecoder(decoder.beginStructure(descriptor))
        }

        inner class DsmCompositeDecoder(private val compositeDecoder: CompositeDecoder) :
            CompositeDecoder by compositeDecoder {
            override fun <T> decodeSerializableElement(
                descriptor: SerialDescriptor,
                index: Int,
                deserializer: DeserializationStrategy<T>,
                previousValue: T?,
            ): T {
                return when (deserializer) {
                    ByteArraySerializer(), is MapLikeSerializer<*, *, *, *> -> {
                        compositeDecoder.decodeSerializableElement(descriptor, index, deserializer)
                    }
                    is AbstractCollectionSerializer<*, *, *> -> {
                        val elementDescriptor = deserializer.descriptor.getElementDescriptor(0)
                        if (elementDescriptor.kind !is PrimitiveKind) {
                            val elementClass = getClass(elementDescriptor, classLoader)

                            val kSerializer = elementClass.dsmSerializer(classLoader = classLoader)
                            val ids = decoder.decodeSerializableValue(ListSerializer(String.serializer()))
                            val list = findByIds(ids, elementClass, kSerializer)
                            unchecked(list.parseCollection(deserializer))
                        } else compositeDecoder.decodeSerializableElement(
                            descriptor,
                            index,
                            deserializer as KSerializer<T>
                        )
                    }
                    else -> {
                        val strategy = DsmSerializer(deserializer as KSerializer<T>, classLoader)
                        compositeDecoder.decodeSerializableElement(descriptor, index, strategy)
                    }
                }
            }
        }
    }

    private fun getClass(elementDescriptor: SerialDescriptor, classLoader: ClassLoader): KClass<Any> {
        @Suppress("UNCHECKED_CAST")
        return classLoader.loadClass(elementDescriptor.serialName).kotlin as KClass<Any>
    }

    //todo rmeove
    private fun curClassLoader() = Thread.currentThread().contextClassLoader
}

private fun Iterable<Any>.parseCollection(
    des: AbstractCollectionSerializer<*, *, *>,
): Any = run {
    firstOrNull()?.let { it::class.serializer() }?.let { serializer ->
        when (des::class) {
            ListSerializer(serializer)::class -> toMutableList()
            SetSerializer(serializer)::class -> toMutableSet()
            else -> TODO("not implemented yet")
        }
    } ?: this
}

inline fun <reified T : Any> dsmDecode(inputStream: InputStream, classLoader: ClassLoader): T = json.decodeFromStream(
    T::class.dsmSerializer(classLoader = classLoader),
    inputStream
)

inline fun <reified T : Any> dsmDecode(inputJson: String, classLoader: ClassLoader): T =
    json.decodeFromString(
        T::class.dsmSerializer(classLoader = classLoader),
        inputJson
    )

inline fun <reified T : Any> classLoader(): ClassLoader = T::class.java.classLoader!!

