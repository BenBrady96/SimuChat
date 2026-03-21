// =============================================================================
// SimuChat — Authentication Routes
// =============================================================================
// Handles user registration and login. These routes are public (no JWT
// required) as they are used to obtain a token in the first place.
// =============================================================================

package com.simuchat.routes

import com.simuchat.models.AuthRequest
import com.simuchat.services.AccountLockedException
import com.simuchat.models.AuthResponse
import com.simuchat.models.ErrorResponse
import com.simuchat.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Configures authentication routes under /api.
 * These routes are public and do not require a Bearer token.
 */
fun Route.configureAuthRoutes() {
    val authService = AuthService(application.environment.config)

    route("/api") {

        /**
         * POST /api/register
         *
         * Creates a new user account and returns a JWT token.
         *
         * Request body:  { "username": "string", "password": "string" }
         * Response:      { "token": "string", "username": "string", "message": "string" }
         * Errors:        400 (validation), 409 (username taken)
         */
        post("/register") {
            try {
                val request = call.receive<AuthRequest>()

                // Sanitise username — alphanumeric and underscores only
                if (!request.username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Username may only contain letters, numbers, and underscores")
                    )
                }

                val (token, _) = authService.register(request.username, request.password)
                call.respond(
                    HttpStatusCode.Created,
                    AuthResponse(
                        token = token,
                        username = request.username,
                        message = "Registration successful"
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Registration failed"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request. Please check your input and try again.")
                )
            }
        }

        /**
         * POST /api/login
         *
         * Authenticates a user and returns a JWT token.
         *
         * Request body:  { "username": "string", "password": "string" }
         * Response:      { "token": "string", "username": "string", "message": "string" }
         * Errors:        401 (invalid credentials)
         */
        post("/login") {
            try {
                val request = call.receive<AuthRequest>()
                val (token, _) = authService.login(request.username, request.password)
                call.respond(
                    HttpStatusCode.OK,
                    AuthResponse(
                        token = token,
                        username = request.username,
                        message = "Login successful"
                    )
                )
            } catch (e: AccountLockedException) {
                // 423 Locked — account temporarily locked due to too many failed attempts
                call.respond(
                    HttpStatusCode(423, "Locked"),
                    ErrorResponse(e.message ?: "Account is locked. Please try again later.")
                )
            } catch (e: IllegalArgumentException) {
                // Return the specific error message (includes remaining attempts)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(e.message ?: "Invalid username or password")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request. Please check your input and try again.")
                )
            }
        }
    }
}
