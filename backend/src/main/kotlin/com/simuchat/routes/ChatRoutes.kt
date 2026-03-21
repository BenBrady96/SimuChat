// =============================================================================
// SimuChat — Chat Routes
// =============================================================================
// Protected routes for managing chat threads and messages. All endpoints
// require a valid JWT Bearer token. Handles thread CRUD, message history,
// and AI-powered responses via the Gemini API.
// =============================================================================

package com.simuchat.routes

import com.simuchat.models.*
import com.simuchat.services.ChatService
import com.simuchat.services.GeminiService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch

/**
 * Configures chat routes under /api, all protected by JWT authentication.
 */
fun Route.configureChatRoutes() {
    val chatService = ChatService()
    val config = application.environment.config
    val geminiService = GeminiService(
        apiKey = config.property("gemini.apiKey").getString(),
        model = config.property("gemini.model").getString()
    )

    authenticate("jwt") {
        route("/api") {

            // -----------------------------------------------------------------
            // Characters
            // -----------------------------------------------------------------

            /**
             * GET /api/characters
             *
             * Returns the list of available AI character personas.
             * Used by the frontend to render the character selection screen.
             */
            get("/characters") {
                call.respond(CharacterPersonas.characters)
            }

            // -----------------------------------------------------------------
            // Threads
            // -----------------------------------------------------------------

            /**
             * GET /api/threads?character={name}
             *
             * Returns all chat threads for the authenticated user.
             * Optionally filter by character name using the query parameter.
             */
            get("/threads") {
                val userId = extractUserId(call)
                val characterName = call.request.queryParameters["character"]
                val threads = chatService.getThreadsForUser(userId, characterName)
                call.respond(threads)
            }

            /**
             * POST /api/threads
             *
             * Creates a new chat thread for the specified character.
             *
             * Request body: { "characterName": "string" }
             */
            post("/threads") {
                val userId = extractUserId(call)
                val request = call.receive<CreateThreadRequest>()

                // Validate that the character exists
                val character = CharacterPersonas.findByName(request.characterName)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Unknown character: ${request.characterName}")
                    )

                val thread = chatService.createThread(userId, character.name)
                call.respond(HttpStatusCode.Created, thread)
            }

            /**
             * DELETE /api/threads/{id}
             *
             * Deletes a chat thread and all its messages.
             * Only the thread owner can delete it.
             */
            delete("/threads/{id}") {
                val userId = extractUserId(call)
                val threadId = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid thread ID")
                    )

                try {
                    chatService.deleteThread(threadId, userId)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Thread deleted"))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Thread not found"))
                }
            }

            // -----------------------------------------------------------------
            // Messages
            // -----------------------------------------------------------------

            /**
             * GET /api/threads/{id}/messages
             *
             * Returns all messages in a thread, ordered chronologically.
             */
            get("/threads/{id}/messages") {
                val userId = extractUserId(call)
                val threadId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid thread ID")
                    )

                try {
                    val messages = chatService.getMessages(threadId, userId)
                    call.respond(messages)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Thread not found"))
                }
            }

            /**
             * POST /api/threads/{id}/message
             *
             * Sends a new message to a thread and gets an AI response.
             *
             * Flow:
             * 1. Validates the thread belongs to the user
             * 2. Saves the user's message to the database
             * 3. Fetches the full conversation history
             * 4. Calls the Gemini API with history + system instruction
             * 5. Saves the AI response to the database
             * 6. Returns both messages to the client
             *
             * Request body: { "content": "string" }
             */
            post("/threads/{id}/message") {
                val userId = extractUserId(call)
                val threadId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid thread ID")
                    )
                val request = call.receive<SendMessageRequest>()

                if (request.content.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Message content cannot be empty")
                    )
                }

                // Guard against oversized payloads
                if (request.content.length > 5000) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Message content exceeds maximum length of 5000 characters")
                    )
                }

                try {
                    // 1. Get the thread's character to determine system instructions
                    val threads = chatService.getThreadsForUser(userId)
                    val thread = threads.find { it.id == threadId }
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Thread not found")
                        )

                    val character = CharacterPersonas.findByName(thread.characterName)
                        ?: return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Character configuration not found")
                        )

                    // 2. Save the user's message
                    val userMessage = chatService.addMessage(threadId, userId, "user", request.content)

                    // 3. Get conversation history (excluding the message we just added,
                    //    as we pass it separately to the Gemini API)
                    val history = chatService.getConversationHistory(threadId)
                        .dropLast(1) // Remove the message we just added

                    // 4. Call Gemini API
                    val aiResponseText = try {
                        geminiService.chat(
                            conversationHistory = history,
                            newMessage = request.content,
                            systemInstruction = character.systemInstruction
                        )
                    } catch (e: Exception) {
                        "I apologise, but I'm having trouble responding right now. " +
                                "Please try again in a moment. (Error: ${e.message})"
                    }

                    // 5. Save the AI response
                    val aiMessage = chatService.addMessage(threadId, userId, "model", aiResponseText)

                    // 6. Generate a smart title if this is the first exchange
                    if (thread.title == "New Chat") {
                        val smartTitle = try {
                            geminiService.generateTitle(request.content, aiResponseText)
                        } catch (e: Exception) {
                            if (request.content.length > 50) request.content.take(50) + "..." else request.content
                        }
                        chatService.updateThreadTitle(threadId, userId, smartTitle)
                    }

                    // 7. Return both messages
                    call.respond(
                        HttpStatusCode.OK,
                        ChatResponse(userMessage = userMessage, aiMessage = aiMessage)
                    )

                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Thread not found"))
                }
            }
        }
    }
}

/**
 * Extracts the authenticated user's ID from the JWT principal.
 *
 * @throws IllegalStateException if the JWT principal is missing or invalid
 */
private fun extractUserId(call: ApplicationCall): Int {
    val principal = call.principal<JWTPrincipal>()
        ?: throw IllegalStateException("Missing JWT principal")
    return principal.payload.getClaim("userId").asInt()
}
