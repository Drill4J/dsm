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
@file:Suppress("NOTHING_TO_INLINE")

package com.epam.dsm.util

import com.epam.dsm.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import mu.*
import org.jetbrains.exposed.sql.*
import java.sql.*
import kotlin.reflect.*

internal val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()

val logger = KotlinLogging.logger {}

internal val uuid
    get() = "${java.util.UUID.randomUUID()}"

inline fun <reified T : Any> Transaction.createJsonTable(tableName: String) {
    execWrapper("""
        CREATE TABLE IF NOT EXISTS $tableName (
            $ID_COLUMN varchar(256) not null constraint ${tableName}_pk primary key, 
            $JSON_COLUMN jsonb
        );
   """
    )
    createCollectionTrigger<T>(tableName)
    commit()
}

fun Transaction.createBinaryTable() {
    execWrapper(
        """
             CREATE TABLE IF NOT EXISTS BINARYA ($ID_COLUMN varchar(256) not null constraint binarya_pk primary key, binarya bytea);
             ALTER TABLE BINARYA ALTER COLUMN binarya SET STORAGE EXTERNAL; 
             """.trimIndent()
    )
    commit()
}

inline fun Transaction.createMapTable(tableName: String) {
    execWrapper("CREATE TABLE IF NOT EXISTS $tableName (ID varchar(256) not null constraint ${tableName}_pk primary key, PARENT_ID varchar(256) not null, KEY_JSON jsonb, VALUE_JSON jsonb);")
    commit()
}

inline fun Transaction.execWrapper(
    sqlStatement: String,
    args: Iterable<Pair<IColumnType, Any?>> = emptyList(),
    noinline transform: (ResultSet) -> Unit = {},
) {
    logger.trace { "SQL statement on schema '${connection.schema}': $sqlStatement" }
    exec(sqlStatement, args, transform = transform)
}

// As per https://youtrack.jetbrains.com/issue/KT-10440 there isn't a reliable way to get back a KClass for a
// Kotlin primitive types
val PRIMITIVE_CLASSES = mapOf<SerialKind, KClass<*>>(
    PrimitiveKind.BOOLEAN to Boolean::class,
    PrimitiveKind.BYTE to Byte::class,
    PrimitiveKind.CHAR to Char::class,
    PrimitiveKind.FLOAT to Float::class,
    PrimitiveKind.DOUBLE to Double::class,
    PrimitiveKind.INT to Int::class,
    PrimitiveKind.LONG to Long::class,
    PrimitiveKind.SHORT to Short::class,
    PrimitiveKind.STRING to String::class,
)

fun SerialKind.isNotPrimitive() = PRIMITIVE_CLASSES[this] == null

inline fun <reified T : Any> createCollectionTrigger(tableName: String) {
    val desc = T::class.serializer().descriptor.collectionPaths()

    """ 
        CREATE TRIGGER check_update_delete_$tableName
        BEFORE UPDATE OR DELETE ON plugin.test_overview
        FOR EACH ROW
        EXECUTE PROCEDURE trigger_for_$tableName();
        
        CREATE OR REPLACE FUNCTION trigger_for_$tableName()
         RETURNS TRIGGER LANGUAGE PLPGSQL AS $$ 
          	BEGIN 
          	 	DELETE FROM plugin.LABEL WHERE ID IN ((OLD.json_body -> 'data'->>'lables')::text[]);
          	RETURN NEW;
          	END;
        $$
        
    """.trimIndent()

//    val collections = kClass.declaredMemberProperties.filter {
//        (it.returnType.classifier as? KClass<*>)?.isSubclassOf(Collection::class) ?: false
//    }.mapNotNull {
//        (it.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>)?.let { genericClass ->
//            if (genericClass !in PRIMITIVE_CLASSES.values) {
//                it.name to genericClass.tableName()
//            }
//        }
//    }

}

typealias PathToTable = Pair<String, String>

private val SerialDescriptor.elementsRange
    get() = 0..elementsCount.dec()

fun SerialDescriptor.collectionPaths() = elementsRange.map { index ->
    val pathCollection = mutableListOf<PathToTable>()
    val elementDescriptor = getElementDescriptor(index)
    if (elementDescriptor.kind is StructureKind.CLASS) {
        pathCollection.addAll(pathBuilder(elementDescriptor, "->'${getElementName(index)}'"))
    }

    if (elementDescriptor.kind is StructureKind.LIST) {
        elementDescriptor.elementDescriptors.firstOrNull()?.takeIf { it.kind.isNotPrimitive() }?.let {
            pathCollection.add("->>'${getElementName(index)}'" to it.tableName())
        }
    }
    pathCollection
}.flatten()

fun pathBuilder(parentDesc: SerialDescriptor, path: String = ""): List<PathToTable> {
    val pathCollection = mutableListOf<PathToTable>()
    parentDesc.elementsRange.forEach { index ->
        val currentDesc = parentDesc.getElementDescriptor(index)
        if (currentDesc.kind is StructureKind.CLASS) {
            pathCollection.addAll(
                pathBuilder(
                    currentDesc.getElementDescriptor(index),
                    "$path->'${currentDesc.getElementName(index)}'"
                )
            )
        }
        if (currentDesc.kind is StructureKind.LIST) {
            currentDesc.elementDescriptors.firstOrNull()?.takeIf { PRIMITIVE_CLASSES[it.kind] == null }?.let {
                pathCollection.add("$path->>'${parentDesc.getElementName(index)}'" to it.tableName())
            }
        }
    }
    return pathCollection
}
