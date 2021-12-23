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

import kotlinx.coroutines.*
import kotlin.test.*

class SearchInListTest : PostgresBased("plugin") {
    private val blink = SubObject("subStr", 12, Last(2.toByte()))
    private val testName = "test1"
    private val setPayloadWithTest = SetPayload(id = "first", name = testName, blink)
    private val setPayload = SetPayload(id = "third", name = testName, blink)
    private val payloadWithIdList = PayloadWithIdList(
        id = "1",
        list = listOf(
            setPayloadWithTest,
            SetPayload(id = "second", name = "test2", blink)
        )
    )
    private val payloadWithIdList2 = PayloadWithIdList(
        id = "2",
        list = listOf(setPayload)
    )

    @Test
    fun `should find objects when use to_jsonb`() = runBlocking {
        agentStore.store(payloadWithIdList)
        agentStore.store(payloadWithIdList2)
        val find = agentStore.findInList<PayloadWithIdList, SetPayload>(
            whatReturn = "to_jsonb(items)",
            listWay = "'list'",
            listDescription = "items(\"id\" text, \"name\" text, \"subObject\" jsonb)",
            where = "where name = '$testName'",
        )
        assertEquals(listOf(setPayloadWithTest, setPayload), find)
    }


    @Test
    fun `should find primitives when use operand in jsonb`() = runBlocking {
        agentStore.store(payloadWithIdList)
        agentStore.store(payloadWithIdList2)
        val find = agentStore.findInList<PayloadWithIdList, Int>(
            whatReturn = "\"subObject\"->'int'",
            listWay = "'list'",
            listDescription = "items(\"id\" text, \"name\" text, \"subObject\" jsonb)",
            where = "where name = '$testName'"
        )
        assertEquals(listOf(12, 12), find)
    }

    @Test
    fun `should find nothing when in where cannot find`() = runBlocking {
        agentStore.store(payloadWithIdList)
        agentStore.store(payloadWithIdList2)
        val find = agentStore.findInList<PayloadWithIdList, Int>(
            whatReturn = "to_jsonb(items)",
            listWay = "'list'",
            listDescription = "items(\"id\" text, \"name\" text, \"subObject\" jsonb)",
            where = "where name = 'test3'"
        )
        assertEquals(listOf(), find)
    }

    @Test
    fun `should find all subObjects when not filter`() = runBlocking {
        agentStore.store(payloadWithIdList)
        val find = agentStore.findInList<PayloadWithIdList, SubObject>(
            whatReturn = "\"subObject\"",
            listWay = "'list'",
            listDescription = "items(\"id\" text, \"name\" text, \"subObject\" jsonb)",
        )
        assertEquals(listOf(blink, blink), find)
    }

    @Test
    fun `should skipp null when find it`() = runBlocking {
        agentStore.store(
            PayloadWithIdList(
                id = "1",
                list = listOf(
                    setPayloadWithTest,
                    SetPayload(id = "second", name = "test2")
                )
            )
        )
        val find = agentStore.findInList<PayloadWithIdList, SubObject>(
            whatReturn = "\"subObject\"",
            listWay = "'list'",
            listDescription = "items(\"id\" text, \"name\" text, \"subObject\" jsonb)",
        )
        assertEquals(listOf(blink), find)
    }
}

