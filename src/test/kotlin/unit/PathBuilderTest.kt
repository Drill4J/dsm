package com.epam.dsm.unit

import com.epam.dsm.*
import com.epam.dsm.util.*
import kotlinx.serialization.*
import kotlin.test.*

class PathBuilderTest {

    @Serializable
    private data class ObjectWithListPrimitive(
        val string: String,
        val list: List<String>,
    )

    @Serializable
    private data class ObjectWithObjectWithList(
        val name: String,
        val objectWithObjectList: ObjectWithObjectList,
    )

    @Serializable
    private data class ObjectWithObjectWithTwoList(
        val name: String,
        val objectWithObjectList: ObjectWithListTwoList,
    )

    @Serializable
    private data class ObjectWithObjectWithTwoListWithList(
        val name: String,
        val objectWithObjectList: ObjectWithListTwoList,
        val list: List<SimpleObject>,
    )

    @Serializable
    private data class ObjectWithObjectList(
        val sum: Int,
        val list: List<SimpleObject>,
    )

    @Serializable
    private data class ObjectWithListTwoList(
        val name: String,
        val first: List<SimpleObject>,
        val second: Set<SimpleObject>,
    )


    @Serializable
    private data class SimpleObject(
        val name: String,
    )


    @Test
    fun `primitive list should not be in paths`() {
        assertTrue(ObjectWithListPrimitive::class.serializer().descriptor.collectionPaths().isEmpty())
    }

    @Test
    fun `object with list must be in path`() {
        assertEquals(
            "->>'${ObjectWithObjectList::list.name}'" to SimpleObject::class.tableName(),
            ObjectWithObjectList::class.serializer().descriptor.collectionPaths().first()
        )
    }

    @Test
    fun `nested structure mast be in paths`() {
        assertEquals(
            "->'${ObjectWithObjectWithList::objectWithObjectList.name}'->>'${ObjectWithObjectList::list.name}'" to SimpleObject::class.tableName(),
            ObjectWithObjectWithList::class.serializer().descriptor.collectionPaths().first()
        )
    }

    @Test
    fun `nested structures must be in paths`() {
        assertEquals(
            listOf("->'${ObjectWithObjectWithTwoList::objectWithObjectList.name}'->>'${ObjectWithListTwoList::first.name}'" to SimpleObject::class.tableName(),
                "->'${ObjectWithObjectWithTwoList::objectWithObjectList.name}'->>'${ObjectWithListTwoList::second.name}'" to SimpleObject::class.tableName()),
            ObjectWithObjectWithTwoList::class.serializer().descriptor.collectionPaths()
        )
    }

    @Test
    fun `list with nested structures must be in paths`() {
        assertEquals(
            listOf("->'${ObjectWithObjectWithTwoListWithList::objectWithObjectList.name}'->>'${ObjectWithListTwoList::first.name}'" to SimpleObject::class.tableName(),
                "->'${ObjectWithObjectWithTwoListWithList::objectWithObjectList.name}'->>'${ObjectWithListTwoList::second.name}'" to SimpleObject::class.tableName(),
                "->>'${ObjectWithObjectWithTwoListWithList::list.name}'" to SimpleObject::class.tableName()
            ),
            ObjectWithObjectWithTwoListWithList::class.serializer().descriptor.collectionPaths()
        )
    }


}
