// =============================================================================
// SimuChat — Database Factory
// =============================================================================
// Initialises the PostgreSQL connection pool using HikariCP and creates the
// database schema using Exposed's SchemaUtils. Connection parameters are
// read from the Ktor application configuration (HOCON).
// =============================================================================

package com.simuchat.database

import com.simuchat.models.ChatMessages
import com.simuchat.models.ChatThreads
import com.simuchat.models.Users
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    /**
     * Initialises the database connection and creates tables if they don't exist.
     *
     * @param config Ktor application configuration containing database settings
     */
    fun init(config: ApplicationConfig) {
        val host = config.property("database.host").getString()
        val port = config.property("database.port").getString()
        val dbName = config.property("database.name").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()

        val dataSource = createHikariDataSource(
            url = "jdbc:postgresql://$host:$port/$dbName",
            user = user,
            password = password
        )

        Database.connect(dataSource)

        // Create tables on startup (idempotent — only creates if missing)
        transaction {
            SchemaUtils.create(Users, ChatThreads, ChatMessages)
        }
    }

    /**
     * Configures HikariCP connection pool with sensible defaults.
     * Pool size is kept small, appropriate for an e2-small instance.
     */
    private fun createHikariDataSource(url: String, user: String, password: String): HikariDataSource {
        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 600000        // 10 minutes
            connectionTimeout = 30000   // 30 seconds
            maxLifetime = 1800000       // 30 minutes
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }

    /**
     * Executes a database query within a coroutine-friendly suspended transaction.
     * Uses the IO dispatcher to avoid blocking the main event loop.
     */
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
