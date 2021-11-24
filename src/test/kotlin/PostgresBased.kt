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

import com.zaxxer.hikari.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.*
import org.junit.jupiter.api.*
import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.*
import kotlin.test.*

abstract class PostgresBased(val schema: String) {
    val agentStore = StoreClient(schema)

    @BeforeTest
    fun before() {
        transaction {
            exec("CREATE SCHEMA IF NOT EXISTS $schema")
        }
    }

    @AfterTest
    fun after() {
        transaction {
            exec("DROP SCHEMA $schema CASCADE")
        }
        createdTables.clear()
    }


    companion object {

        lateinit var postgresContainer: PostgreSQLContainer<Nothing>

        @BeforeAll
        @JvmStatic
        fun postgresSetup() {
            postgresContainer = PostgreSQLContainer<Nothing>("postgres:12").apply {
                withDatabaseName("dbName")
                withExposedPorts(PostgreSQLContainer.POSTGRESQL_PORT)
                waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                start()
            }
            println("started container with id ${postgresContainer.containerId}.")
            Database.connect(
                driver = postgresContainer.driverClassName,
                url = postgresContainer.jdbcUrl,
                user = postgresContainer.username,
                password = postgresContainer.password
            )
        }

        @AfterAll
        @JvmStatic
        fun shutDown() {
            postgresContainer.stop()
        }

    }
}
