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
package com.epam.dsm.find

import com.epam.dsm.*
import com.epam.dsm.util.*
import kotlin.reflect.KProperty1

const val EXTRACT_TEXT = "->>"
const val EXTRACT_JSON = "->"

class FieldPath {
    private val path: List<String>

    constructor(vararg properties: KProperty1<*, *>) {
        path = properties.map { it.name }
    }

    constructor(vararg fields: String) {
        path = fields.toList()
    }

    constructor(fields: List<String>) {
        path = fields
    }

    fun extractText(): String {
        return buildSqlPath(EXTRACT_TEXT)
    }

    fun extractJson(): String {
        return buildSqlPath(EXTRACT_JSON)
    }

    private fun buildSqlPath(lastOperation: String): String {
        val sqlPath = if (path.size > 1) {
            path.dropLast(1).joinToString(prefix = "$JSON_COLUMN $EXTRACT_JSON '",
                separator = "' $EXTRACT_JSON '",
                postfix = "'")
        } else {
            JSON_COLUMN
        }
        return "$sqlPath $lastOperation ${path.last().toQuotes()}"
    }
}

/**
 * This class for building 'where' query
 * @see buildSqlCondition
 */
class Expr<Q : Any> {
    val conditions = mutableListOf<String>()

    companion object {
        const val SHIFT_OPERATION = 1
    }

    infix fun Expr<Q>.and(@Suppress("UNUSED_PARAMETER") expression: Expr<Q>): Expr<Q> {
        conditions.add(conditions.size - SHIFT_OPERATION, "AND")
        return this
    }

    infix fun Expr<Q>.or(@Suppress("UNUSED_PARAMETER") expression: Expr<Q>): Expr<Q> {
        conditions.add(conditions.size - SHIFT_OPERATION, "OR")
        return this
    }

    infix fun <R : Comparable<*>> KProperty1<Q, R>.eq(r: R): Expr<Q> {
        val encodeId = r.encodeId()
        conditions.add("""
            ${if (encodeId.startsWith("{")) "$JSON_COLUMN->" else "$JSON_COLUMN->>"}
            ${this@eq.name.toQuotes()}${equal(encodeId)}
            """.trimIndent())
        return this@Expr
    }

    infix fun FieldPath.eq(value: String): Expr<Q> {
        conditions.add("${extractText()} ${equal(value)}")
        return this@Expr
    }

    private fun equal(value: String) = "= ${value.toQuotes()}"

    fun FieldPath.eqNull(): Expr<Q> {
        conditions.add("${extractText()}${this@Expr.eqNull()}")
        return this@Expr
    }

    fun <R : Comparable<*>> KProperty1<Q, R>.eqNull(): Expr<Q> {
        conditions.add("$JSON_COLUMN->>${this@eqNull.name.toQuotes()}${this@Expr.eqNull()}")
        return this@Expr
    }

    private fun eqNull() = " is null"

    infix fun <R : Comparable<*>> KProperty1<Q, R>.startsWith(prefix: String): Expr<Q> {
        conditions.add("$JSON_COLUMN->> ${this@startsWith.name.toQuotes()} like '$prefix%'")
        return this@Expr
    }

    infix fun <R : Comparable<*>> KProperty1<Q, R>.contains(values: List<String>): Expr<Q> {
        conditions.add("$JSON_COLUMN->> ${this@contains.name.toQuotes()}${values.toSqlIn()}")
        return this@Expr
    }

    infix fun FieldPath.containsWithNull(values: List<String>): Expr<Q> {
        val pathField = extractText()
        conditions.add("($pathField ${values.toSqlIn()} OR $pathField${this@Expr.eqNull()})")
        return this@Expr
    }

    infix fun FieldPath.contains(values: List<String>): Expr<Q> {
        conditions.add("${extractText()} ${values.toSqlIn()}")
        return this@Expr
    }

    infix fun Expr<Q>.containsId(list: List<String>): Expr<Q> {
        conditions.add("$ID_COLUMN ${list.toSqlIn()}")
        return this
    }

    infix fun Expr<Q>.containsParentId(list: List<String>): Expr<Q> {
        conditions.add("$PARENT_ID_COLUMN ${list.toSqlIn()}")
        return this
    }

}

inline fun <reified T : Any> buildSqlCondition(expression: Expr<T>.() -> Unit) =
    Expr<T>().run { expression(this);conditions.joinToString(" ") }

fun List<String>.toSqlIn(): String = this.joinToString(prefix = "in ('", postfix = "')", separator = "', '")
