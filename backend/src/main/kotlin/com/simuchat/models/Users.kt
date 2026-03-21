// =============================================================================
// SimuChat — Users Table Definition
// =============================================================================
// Stores registered user accounts with BCrypt-hashed passwords.
// Includes failed login attempt tracking for account lockout security.
// =============================================================================

package com.simuchat.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Users table — stores authentication credentials and lockout state.
 *
 * | Column              | Type         | Constraints       |
 * |---------------------|--------------|-------------------|
 * | id                  | INT (auto)   | Primary key       |
 * | username            | VARCHAR(50)  | Unique, not null  |
 * | password_hash       | VARCHAR(255) | Not null          |
 * | failed_attempts     | INT          | Default 0         |
 * | locked_until        | TIMESTAMP    | Nullable          |
 */
object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val failedAttempts = integer("failed_attempts").default(0)
    val lockedUntil = datetime("locked_until").nullable()

    override val primaryKey = PrimaryKey(id)
}
