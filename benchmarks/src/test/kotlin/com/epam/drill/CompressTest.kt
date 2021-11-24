package com.epam.drill

import com.epam.dsm.*
import com.epam.dsm.serializer.*
import com.zaxxer.hikari.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.*
import org.openjdk.jmh.annotations.*
import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.*
import java.io.*
import java.util.concurrent.*

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5, timeUnit = TimeUnit.MILLISECONDS)
class CompressTest {

    companion object {
        init {
            val postgresContainer = PostgreSQLContainer<Nothing>("postgres:12").apply {
                withDatabaseName("dbName")
                withExposedPorts(PostgreSQLContainer.POSTGRESQL_PORT)
                waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                start()
            }
            println("started container with id ${postgresContainer.containerId}.")
            DatabaseFactory.init(HikariDataSource(HikariConfig().apply {
                this.driverClassName = postgresContainer.driverClassName
                this.jdbcUrl = postgresContainer.jdbcUrl
                this.username = postgresContainer.username
                this.password = postgresContainer.password
                this.maximumPoolSize = 3
                this.isAutoCommit = false
                this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                this.validate()
            }))
        }

    }

    private val filePath = ""
    private val bytes = File(filePath).readBytes()
    private val schema = "binary_perf_test"
    private val store = StoreClient(schema)

    @Setup
    fun before() {
        transaction {
            exec("CREATE SCHEMA IF NOT EXISTS $schema")
        }
        println("created schema")
    }

    @TearDown
    fun after() {
        Thread.sleep(500)
        println("after benchmark...")
        transaction {
            val result = connection.prepareStatement(
                "SELECT pg_size_pretty(pg_total_relation_size('$schema.BINARYA'))", false
            ).executeQuery()
            result.next()
            println("Table size ${result.getString(1)}")
        }
        transaction {
            exec("DROP SCHEMA $schema CASCADE")
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    fun test() = runBlocking {
        store.store(BinaryClass("${java.util.UUID.randomUUID()}", bytes))
    }
}

@Serializable
data class BinaryClass(
    @Id
    val id: String,
    @Suppress("ArrayInDataClass")
    @Serializable(with = BinarySerializer::class)
    val bytes: ByteArray,
)
