// =============================================================================
// SimuChat — ChatMessages Table Definition
// =============================================================================
// Stores individual messages within a chat thread. Each message has a role
// ('user' or 'model') matching the Gemini API's expected format, making it
// straightforward to reconstruct conversation history for API calls.
// =============================================================================

package com.simuchat.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * ChatMessages table — stores the full conversation history.
 *
 * | Column    | Type          | Constraints                        |
 * |-----------|---------------|------------------------------------|
 * | id        | INT (auto)    | Primary key                        |
 * | thread_id | INT           | FK → ChatThreads, cascade delete   |
 * | role      | VARCHAR(10)   | 'user' or 'model'                  |
 * | content   | TEXT          | The message body                   |
 * | timestamp | TIMESTAMP     | Defaults to current time           |
 */
object ChatMessages : Table("chat_messages") {
    val id = integer("id").autoIncrement()
    val threadId = integer("thread_id").references(ChatThreads.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 10)     // 'user' or 'model'
    val content = text("content")
    val timestamp = datetime("timestamp").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}
