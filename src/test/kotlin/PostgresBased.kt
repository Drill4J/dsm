package com.epam.dsm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres
import ru.yandex.qatools.embed.postgresql.distribution.Version

abstract class PostgresBased {
    companion object {
        lateinit var postgres: EmbeddedPostgres

        @BeforeAll
        @JvmStatic
        fun postgresSetup() {
            postgres = EmbeddedPostgres(Version.V10_6)
            val host = "localhost"
            val port = 5432
            val dbName = "dbName"
            val userName = "userName"
            val password = "password"
            postgres.start(
                host,
                port,
                dbName,
                userName,
                password
            )
            Thread.sleep(5000) //todo :) timeout
            DatabaseFactory.init(HikariDataSource(HikariConfig().apply {
                this.driverClassName = "org.postgresql.Driver"
                this.jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
                this.username = userName
                this.password = password
                this.maximumPoolSize = 3
                this.isAutoCommit = false
                this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                this.validate()
            }))
        }

        @AfterAll
        @JvmStatic
        fun postgresClean() {
            postgres.close()
        }

    }
}