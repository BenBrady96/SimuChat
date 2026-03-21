// =============================================================================
// SimuChat — Data Transfer Objects (DTOs)
// =============================================================================
// Serialisable data classes for API request/response payloads.
// These are decoupled from the database models to maintain clean separation
// between the persistence layer and the API contract.
// =============================================================================

package com.simuchat.models

import kotlinx.serialization.Serializable

// ---- Authentication DTOs ----

/** Request body for POST /api/register and POST /api/login */
@Serializable
data class AuthRequest(
    val username: String,
    val password: String
)

/** Response body containing the JWT token after successful auth */
@Serializable
data class AuthResponse(
    val token: String,
    val username: String,
    val message: String
)

// ---- Thread DTOs ----

/** Request body for POST /api/threads */
@Serializable
data class CreateThreadRequest(
    val characterName: String
)

/** Response body representing a chat thread */
@Serializable
data class ThreadResponse(
    val id: Int,
    val characterName: String,
    val title: String,
    val createdAt: String
)

// ---- Message DTOs ----

/** Request body for POST /api/threads/{id}/message */
@Serializable
data class SendMessageRequest(
    val content: String
)

/** Response body representing a single chat message */
@Serializable
data class MessageResponse(
    val id: Int,
    val threadId: Int,
    val role: String,
    val content: String,
    val timestamp: String
)

/** Response body after sending a message (includes both user and AI messages) */
@Serializable
data class ChatResponse(
    val userMessage: MessageResponse,
    val aiMessage: MessageResponse
)

// ---- Error DTO ----

/** Standardised error response */
@Serializable
data class ErrorResponse(
    val error: String
)
