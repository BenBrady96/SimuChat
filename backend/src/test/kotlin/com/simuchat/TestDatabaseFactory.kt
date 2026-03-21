// =============================================================================
// SimuChat — Test Database Factory
// =============================================================================
// Provides an H2 in-memory database for unit tests. This avoids the need for
// a running PostgreSQL instance and ensures tests run in complete isolation.
// =============================================================================

package com.simuchat

import com.simuchat.models.ChatMessages
import com.simuchat.models.ChatThreads
import com.simuchat.models.Users
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Initialises an H2 in-memory database with the full SimuChat schema.
 * Call this before running any test that touches the database.
 */
object TestDatabaseFactory {

    private var initialised = false

    fun init() {
        if (initialised) return

        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )

        transaction {
            SchemaUtils.create(Users, ChatThreads, ChatMessages)
        }

        initialised = true
    }

    /**
     * Clears all data between tests for a clean slate.
     */
    fun reset() {
        transaction {
            SchemaUtils.drop(ChatMessages, ChatThreads, Users)
            SchemaUtils.create(Users, ChatThreads, ChatMessages)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
