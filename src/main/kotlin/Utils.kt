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

import com.epam.dsm.serializer.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import org.postgresql.util.*
import java.io.*
import java.util.*
import java.util.stream.*
import kotlin.math.*
import kotlin.reflect.*
import kotlin.reflect.full.*


val json = Json {
    allowStructuredMapKeys = true
}

inline fun <reified T : Any> T.id(): Int {
    val idName = idName(T::class.serializer().descriptor)
    val propertyWithId = T::class.memberProperties.find { it.name == idName }
    return propertyWithId?.getter?.invoke(this)?.hashCode() ?: throw RuntimeException("Property with @Id doesn't found")
}

fun idName(desc: SerialDescriptor): String? = (0 until desc.elementsCount).firstOrNull { index ->
    desc.getElementAnnotations(index).any { it is Id }
}?.let { idIndex -> desc.getElementName(idIndex) }

fun Any.encodeId(): String = when (this) {
    is String -> this
    is Enum<*> -> toString()
    else -> json.encodeToString(unchecked(this::class.serializer()), this)
}

fun <T : Any> KClass<T>.dsmSerializer(
    classLoader: ClassLoader,
    parentId: Int? = null,
): KSerializer<T> = DsmSerializer(this.serializer(), classLoader, parentId)

@Suppress("UNCHECKED_CAST")
fun <T> unchecked(any: Any) = any as T

fun readerToString(value: Reader, maxLength: Int): String? {
    return try {
        val bufferSize = min(maxLength, 1024)
        val result = StringBuilder(bufferSize)
        val buf = CharArray(bufferSize)
        var nRead = 0
        while (nRead > -1 && result.length < maxLength) {
            nRead = value.read(buf, 0, min(bufferSize, maxLength - result.length))
            if (nRead > 0) {
                result.append(buf, 0, nRead)
            }
        }
        result.toString()
    } catch (ioe: IOException) {
        throw PSQLException(GT.tr("Provided Reader failed."), PSQLState.UNEXPECTED_ERROR, ioe)
    }
}

fun elementId(index: Int, parentId: Int?) = "$parentId$index"

fun KSerializer<*>.isBitSet() = descriptor.serialName == BitSet::class.simpleName
