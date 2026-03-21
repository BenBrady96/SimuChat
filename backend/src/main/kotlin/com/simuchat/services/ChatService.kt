// =============================================================================
// SimuChat — Chat Service
// =============================================================================
// CRUD operations for chat threads and messages. Handles creating threads,
// retrieving thread lists, fetching message history, and persisting new
// messages to the database.
// =============================================================================

package com.simuchat.services

import com.simuchat.database.DatabaseFactory.dbQuery
import com.simuchat.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatService {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Retrieves all chat threads for a user, optionally filtered by character.
     * Results are ordered by creation date (newest first).
     */
    suspend fun getThreadsForUser(userId: Int, characterName: String? = null): List<ThreadResponse> = dbQuery {
        val query = ChatThreads.selectAll().where { ChatThreads.userId eq userId }

        if (characterName != null) {
            query.andWhere { ChatThreads.characterName eq characterName }
        }

        query.orderBy(ChatThreads.createdAt, SortOrder.DESC)
            .map { row ->
                ThreadResponse(
                    id = row[ChatThreads.id],
                    characterName = row[ChatThreads.characterName],
                    title = row[ChatThreads.title],
                    createdAt = row[ChatThreads.createdAt].format(dateFormatter)
                )
            }
    }

    /**
     * Creates a new chat thread for a user and character.
     * The title defaults to "New Chat" and is updated after the first message.
     */
    suspend fun createThread(userId: Int, characterName: String): ThreadResponse = dbQuery {
        val now = LocalDateTime.now()
        val id = ChatThreads.insert {
            it[ChatThreads.userId] = userId
            it[ChatThreads.characterName] = characterName
            it[ChatThreads.title] = "New Chat"
            it[ChatThreads.createdAt] = now
        } get ChatThreads.id

        ThreadResponse(
            id = id,
            characterName = characterName,
            title = "New Chat",
            createdAt = now.format(dateFormatter)
        )
    }

    /**
     * Retrieves all messages in a thread, ordered chronologically.
     * Also verifies that the thread belongs to the specified user.
     *
     * @throws IllegalArgumentException if the thread doesn't exist or doesn't belong to the user
     */
    suspend fun getMessages(threadId: Int, userId: Int): List<MessageResponse> = dbQuery {
        // Verify thread ownership
        val thread = ChatThreads.selectAll()
            .where { (ChatThreads.id eq threadId) and (ChatThreads.userId eq userId) }
            .singleOrNull() ?: throw IllegalArgumentException("Thread not found")

        ChatMessages.selectAll()
            .where { ChatMessages.threadId eq threadId }
            .orderBy(ChatMessages.timestamp, SortOrder.ASC)
            .map { row ->
                MessageResponse(
                    id = row[ChatMessages.id],
                    threadId = row[ChatMessages.threadId],
                    role = row[ChatMessages.role],
                    content = row[ChatMessages.content],
                    timestamp = row[ChatMessages.timestamp].format(dateFormatter)
                )
            }
    }

    /**
     * Adds a new message to a thread and returns it.
     * If this is the first user message, updates the thread title to a snippet.
     */
    suspend fun addMessage(threadId: Int, userId: Int, role: String, content: String): MessageResponse = dbQuery {
        // Verify thread ownership
        val thread = ChatThreads.selectAll()
            .where { (ChatThreads.id eq threadId) and (ChatThreads.userId eq userId) }
            .singleOrNull() ?: throw IllegalArgumentException("Thread not found")

        val now = LocalDateTime.now()
        val id = ChatMessages.insert {
            it[ChatMessages.threadId] = threadId
            it[ChatMessages.role] = role
            it[ChatMessages.content] = content
            it[ChatMessages.timestamp] = now
        } get ChatMessages.id

        MessageResponse(
            id = id,
            threadId = threadId,
            role = role,
            content = content,
            timestamp = now.format(dateFormatter)
        )
    }

    /**
     * Retrieves the raw conversation history for a thread, formatted for the
     * Gemini API (list of role/content pairs).
     */
    suspend fun getConversationHistory(threadId: Int): List<Pair<String, String>> = dbQuery {
        ChatMessages.selectAll()
            .where { ChatMessages.threadId eq threadId }
            .orderBy(ChatMessages.timestamp, SortOrder.ASC)
            .map { row ->
                Pair(row[ChatMessages.role], row[ChatMessages.content])
            }
    }

    /**
     * Deletes a thread and all its messages (cascade).
     * Verifies thread ownership before deletion.
     *
     * @throws IllegalArgumentException if the thread doesn't exist or doesn't belong to the user
     */
    suspend fun deleteThread(threadId: Int, userId: Int) = dbQuery {
        val deleted = ChatThreads.deleteWhere {
            (ChatThreads.id eq threadId) and (ChatThreads.userId eq userId)
        }
        if (deleted == 0) {
            throw IllegalArgumentException("Thread not found")
        }
    }

    /**
     * Updates the title of a chat thread.
     * Called by the route layer after Gemini generates a smart title.
     *
     * @param threadId The thread to update
     * @param userId The thread owner (for ownership verification)
     * @param title The new title
     */
    suspend fun updateThreadTitle(threadId: Int, userId: Int, title: String) = dbQuery {
        ChatThreads.update({
            (ChatThreads.id eq threadId) and (ChatThreads.userId eq userId)
        }) {
            it[ChatThreads.title] = title
        }
    }
}
