// =============================================================================
// SimuChat — Gemini API Service
// =============================================================================
// Integrates with Google's Gemini API for multi-turn AI conversations.
// Builds the request payload with conversation history and character system
// instructions, then parses the response text.
// =============================================================================

package com.simuchat.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class GeminiService(private val apiKey: String, private val model: String) {

    // Ktor HTTP client configured for JSON communication with the Gemini API
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Sends a multi-turn conversation to the Gemini API and returns the
     * model's response text.
     *
     * @param conversationHistory List of (role, content) pairs representing previous messages
     * @param newMessage The new user message to send
     * @param systemInstruction Character-specific system instruction to prepend
     * @return The model's generated text response
     * @throws RuntimeException if the API call fails
     */
    suspend fun chat(
        conversationHistory: List<Pair<String, String>>,
        newMessage: String,
        systemInstruction: String
    ): String {
        // Build the contents array with conversation history + new message
        val contents = buildJsonArray {
            // Include previous messages from the thread
            for ((role, content) in conversationHistory) {
                addJsonObject {
                    put("role", role)
                    putJsonArray("parts") {
                        addJsonObject { put("text", content) }
                    }
                }
            }

            // Add the new user message
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", newMessage) }
                }
            }
        }

        // Construct the full request payload with system instructions
        val requestBody = buildJsonObject {
            // System instruction — defines the character's personality
            putJsonObject("system_instruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", systemInstruction) }
                }
            }

            put("contents", contents)

            // Generation configuration
            putJsonObject("generationConfig") {
                put("temperature", 0.9)        // Slightly creative for character voices
                put("topP", 0.95)
                put("topK", 40)
                put("maxOutputTokens", 800)     // Kept low for concise responses
            }
        }

        // Make the API request
        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val response = httpClient.post(apiUrl) {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        // Parse the response — handle specific HTTP error codes
        if (response.status != HttpStatusCode.OK) {
            val statusCode = response.status.value
            val errorBody = response.bodyAsText()

            val userMessage = when (statusCode) {
                429 -> "I'm receiving too many requests right now. Please wait a moment and try again."
                404 -> "The AI model is currently unavailable. Please try again later."
                403 -> "There's an issue with the AI service configuration. Please contact the administrator."
                in 500..599 -> "The AI service is temporarily unavailable. Please try again shortly."
                else -> "Something went wrong with the AI service (Error $statusCode). Please try again."
            }

            // Log the full error for debugging but return a user-friendly message
            System.err.println("Gemini API error ($statusCode): $errorBody")
            throw RuntimeException(userMessage)
        }

        val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val candidates = responseJson["candidates"]?.jsonArray
            ?: throw RuntimeException("No candidates in Gemini response")

        val firstCandidate = candidates.firstOrNull()?.jsonObject
            ?: throw RuntimeException("Empty candidates array in Gemini response")

        val parts = firstCandidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?: throw RuntimeException("No content parts in Gemini response")

        // Extract and concatenate all text parts
        return parts.joinToString("") { part ->
            part.jsonObject["text"]?.jsonPrimitive?.content ?: ""
        }
    }

    /**
     * Generates a short, descriptive title for a chat thread based on the
     * conversation content. Similar to how Gemini/ChatGPT auto-titles chats.
     *
     * @param userMessage The user's first message
     * @param aiResponse The AI's first response
     * @return A concise title (5-8 words) summarising the conversation topic
     */
    suspend fun generateTitle(userMessage: String, aiResponse: String): String {
        val prompt = """
            Based on this conversation, generate a very short title (maximum 6 words) 
            that summarises what the conversation is about. Return ONLY the title text, 
            nothing else. Do not use quotes or punctuation at the end.
            
            User: $userMessage
            Assistant: ${aiResponse.take(500)}
        """.trimIndent()

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.3)       // Low temperature for consistent, factual titles
                put("maxOutputTokens", 30)    // Titles are very short
            }
        }

        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        return try {
            val response = httpClient.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            if (response.status != HttpStatusCode.OK) {
                return userMessage.take(50) // Fallback to first 50 chars
            }

            val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val text = responseJson["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
                ?.trim()

            // Clean up the title — remove any quotes or trailing punctuation
            text?.removeSurrounding("\"")?.trimEnd('.', '!', '?') ?: userMessage.take(50)
        } catch (e: Exception) {
            // Fallback to a snippet of the user's message if title generation fails
            if (userMessage.length > 50) userMessage.take(50) + "..." else userMessage
        }
    }

    /**
     * Closes the HTTP client. Call when the application shuts down.
     */
    fun close() {
        httpClient.close()
    }
}
