package com.epam.dsm.util

import org.jetbrains.exposed.sql.Transaction
import java.sql.ResultSet
import kotlin.reflect.KClass


val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()

fun KClass<*>.toTableName(): String {
    return camelRegex.replace(this.simpleName!!) {
        "_${it.value}"
    }.toLowerCase()
}

fun Transaction.createJsonTable(schema: String, simpleName: String) {
    execWrapper("CREATE TABLE IF NOT EXISTS $schema.$simpleName (ID varchar(256) not null constraint ${simpleName}_pk primary key, JSON_BODY jsonb); ")
}

fun Transaction.createBitwiseTable(schema: String) {
    execWrapper("CREATE TABLE IF NOT EXISTS $schema.BITSET (ID varchar(256) not null constraint bitset_pk primary key, bitset BIT VARYING(10000000)); ")
}

fun Transaction.putBitset(schema: String, id: String, value: String) {
    execWrapper("INSERT INTO $schema.BITSET VALUES ('$id', B'$value');")
}

fun Transaction.execWrapper(
    sqlStatement: String,
    transform: (ResultSet) -> Unit = {}
) {
    println(sqlStatement) // todo mu logger
    exec(sqlStatement, transform = transform)
}