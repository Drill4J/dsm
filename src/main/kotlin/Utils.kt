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

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import kotlin.reflect.full.*


val json = Json {
    allowStructuredMapKeys = true
    coerceInputValues = true
}

inline fun <reified T : Any> idPair(any: T): Pair<String?, Any?> {
    val idName = idName(T::class.serializer().descriptor)
    return idName to (T::class.memberProperties.find { it.name == idName })?.getter?.invoke(any)
}

fun idName(desc: SerialDescriptor): String? = (0 until desc.elementsCount).firstOrNull { index ->
    desc.getElementAnnotations(index).any { it is Id }
}?.let { idIndex -> desc.getElementName(idIndex) }


fun Any.encodeId(): String = this as? String ?: (if(this is Enum<*>) this.toString() else null)?: json.encodeToString(unchecked(this::class.serializer()), this)

@Suppress("UNCHECKED_CAST")
fun <T> unchecked(any: Any) = any as T
