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
import com.epam.dsm.common.*
import com.epam.dsm.common.PrepareData.Companion.payloadWithIdList
import com.epam.dsm.common.PrepareData.Companion.storeLists
import com.epam.dsm.common.PrepareData.Companion.setPayload
import com.epam.dsm.common.PrepareData.Companion.setPayloadTest2
import com.epam.dsm.common.PrepareData.Companion.setPayloadWithTest
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.test.Test

class QueryTest : PostgresBased("query") {
    @BeforeTest
    fun setUp() = runBlocking {
        storeClient.storeLists()
    }

    @Test
    fun `should return objects when findBy use query in default approach`() = runBlocking {
        val query: SearchQuery<PayloadWithIdList> = storeClient.findBy { PayloadWithIdList::id eq "1" }

        val result: List<PayloadWithIdList> = query.get()
        assertEquals(1, result.size)
        assertEquals(payloadWithIdList, result.first())
    }

    @Test
    fun `should find list of strings when execute with the field of sting`() = runBlocking {
        val query: SearchQuery<PayloadWithIdList> = storeClient.findBy { PayloadWithIdList::id eq "1" }

        assertEquals(listOf("1"), query.getAndMap(PayloadWithIdList::id))
    }

    @Test
    fun `should find string values when pass the params`() = runBlocking {
        val query: SearchQuery<PayloadWithIdList> = storeClient.findBy { PayloadWithIdList::id eq "1" }
        assertEquals(listOf("42"), query.getStrings(PayloadWithIdList::num.name))
    }

    @Test
    fun `should find ids when pass conditions`() = runBlocking {
        val query: SearchQuery<PayloadWithIdList> = storeClient.findBy { PayloadWithIdList::id eq "1" }

        assertEquals(listOf("49"), query.getIds())
    }

    @Test
    fun `should find list of list stings when execute with the list of objects`() = runBlocking {
        val findBy: SearchQuery<PayloadWithIdList> = storeClient.findBy { PayloadWithIdList::id eq "1" }
        assertEquals(listOf("490", "491"), findBy.getListIds(PayloadWithIdList::list.name))
    }

    @Test
    fun `should find in list table when pass the ids`() = runBlocking {
        val query = storeClient.findBy<SetPayload> {
            containsId(listOf("490", "500"))
        }
        assertEquals(listOf(setPayloadWithTest, setPayload), query.get())
    }

    @Test
    fun `should find in list table when pass the parent ids`() = runBlocking {
        val query = storeClient.findBy<SetPayload> {
            containsParentId(listOf("49"))
        }
        assertEquals(listOf(setPayloadWithTest, setPayloadTest2), query.get())
    }

    @Test
    fun `should find in list table when pass the ids and additional queries`() = runBlocking {
        val query = storeClient.findBy<SetPayload> {
            containsParentId(listOf("49")) and (SetPayload::id eq "first")
        }
        assertEquals(listOf(setPayloadWithTest), query.get())
    }

}


