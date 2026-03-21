// =============================================================================
// SimuChat — ChatThreads Table Definition
// =============================================================================
// Each thread belongs to a user and a specific AI character. The title is
// auto-generated from the first message to give the thread a readable name
// (similar to how ChatGPT/Gemini name their conversations).
// =============================================================================

package com.simuchat.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * ChatThreads table — represents individual conversations.
 *
 * | Column         | Type          | Constraints                    |
 * |----------------|---------------|--------------------------------|
 * | id             | INT (auto)    | Primary key                    |
 * | user_id        | INT           | FK → Users, cascade delete     |
 * | character_name | VARCHAR(100)  | Not null                       |
 * | title          | VARCHAR(255)  | Auto-generated from 1st msg    |
 * | created_at     | TIMESTAMP     | Defaults to current time       |
 */
object ChatThreads : Table("chat_threads") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val characterName = varchar("character_name", 100)
    val title = varchar("title", 255).default("New Chat")
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}
