// =============================================================================
// SimuChat — Application Entry Point
// =============================================================================
// Configures and starts the Ktor server with all required plugins:
// JSON serialisation, CORS, JWT authentication, error handling, and routing.
// =============================================================================

package com.simuchat

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.simuchat.database.DatabaseFactory
import com.simuchat.routes.configureAuthRoutes
import com.simuchat.routes.configureChatRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // -------------------------------------------------------------------------
    // Initialise Database
    // -------------------------------------------------------------------------
    DatabaseFactory.init(environment.config)

    // -------------------------------------------------------------------------
    // Content Negotiation — JSON serialisation/deserialisation
    // -------------------------------------------------------------------------
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // -------------------------------------------------------------------------
    // CORS — Allow cross-origin requests from the React frontend
    // -------------------------------------------------------------------------
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        // Restrict origins to known frontends only.
        // In production, replace with the actual deployed domain.
        allowHost("localhost:3000")  // Docker Compose frontend
        allowHost("localhost:5173")  // Vite dev server
    }

    // -------------------------------------------------------------------------
    // JWT Authentication
    // -------------------------------------------------------------------------
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("jwt") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                // Extract user ID and username from the JWT claims
                val userId = credential.payload.getClaim("userId")?.asInt()
                val username = credential.payload.getClaim("username")?.asString()
                if (userId != null && username != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Token is invalid or has expired")
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Status Pages — Structured error responses
    // -------------------------------------------------------------------------
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            // Return a generic error message — never expose internal details
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "An unexpected error occurred. Please try again later.")
            )
        }
    }

    // -------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------
    routing {
        // Health check endpoint (used by K8s probes and Docker HEALTHCHECK)
        get("/api/health") {
            call.respond(mapOf("status" to "healthy", "service" to "simuchat-backend"))
        }

        // Public authentication routes
        configureAuthRoutes()

        // Protected chat routes (require valid JWT)
        configureChatRoutes()
    }
}
