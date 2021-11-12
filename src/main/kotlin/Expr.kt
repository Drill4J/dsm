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

import kotlin.reflect.KProperty1


class Expr<Q : Any> {
    val conditions = mutableListOf<String>()

    @Suppress("IMPLICIT_CAST_TO_ANY")
    infix fun <R : Comparable<*>> KProperty1<Q, R>.eq(r: R): Expr<Q> {
        val encodeId = r.encodeId()
        conditions.add("${if (encodeId.startsWith("{")) "JSON_BODY->" else "JSON_BODY->>"}'${this@eq.name}' = '$encodeId'")
        return this@Expr
    }


    infix fun Expr<Q>.and(@Suppress("UNUSED_PARAMETER") expression: Expr<Q>): Expr<Q> {
        conditions.add(conditions.size - 1, "AND")
        return this
    }

    infix fun <Q, R : Comparable<*>> KProperty1<Q, R>.startsWith(prefix: String) {
        conditions.add("JSON_BODY->> '${this@startsWith.name}' like '$prefix%'")
    }

}
