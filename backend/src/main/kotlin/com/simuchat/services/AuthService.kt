// =============================================================================
// SimuChat — Authentication Service
// =============================================================================
// Handles user registration, login, and JWT token generation.
// Passwords are hashed using BCrypt with a cost factor of 12.
// Includes account lockout: 3 failed attempts locks the account for 1 hour.
// =============================================================================

package com.simuchat.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.simuchat.database.DatabaseFactory.dbQuery
import com.simuchat.models.Users
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import java.util.*

/**
 * Custom exception for account lockout, includes the remaining lockout duration.
 */
class AccountLockedException(val minutesRemaining: Long) :
    Exception("Account is locked. Please try again in $minutesRemaining minute(s).")

class AuthService(config: ApplicationConfig) {

    private val jwtSecret = config.property("jwt.secret").getString()
    private val jwtIssuer = config.property("jwt.issuer").getString()
    private val jwtAudience = config.property("jwt.audience").getString()
    private val jwtExpiration = config.property("jwt.expiration").getString().toLong()

    companion object {
        // BCrypt cost factor — 12 balances security with performance on e2-small
        private const val BCRYPT_COST = 12

        // Account lockout settings
        private const val MAX_FAILED_ATTEMPTS = 3
        private const val LOCKOUT_DURATION_MINUTES = 60L // 1 hour
    }

    /**
     * Registers a new user account.
     *
     * @param username The desired username (must be unique)
     * @param password The plaintext password (will be BCrypt-hashed)
     * @return JWT token string on success
     * @throws IllegalArgumentException if the username already exists
     */
    suspend fun register(username: String, password: String): Pair<String, Int> {
        // Validate input
        require(username.length in 3..50) { "Username must be between 3 and 50 characters" }
        require(password.length >= 6) { "Password must be at least 6 characters" }

        // Check if username already exists
        val existingUser = dbQuery {
            Users.selectAll().where { Users.username eq username }.singleOrNull()
        }
        if (existingUser != null) {
            throw IllegalArgumentException("Username '$username' is already taken")
        }

        // Hash the password and insert the new user
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST))
        val userId = dbQuery {
            Users.insert {
                it[Users.username] = username
                it[Users.passwordHash] = passwordHash
                it[Users.failedAttempts] = 0
                it[Users.lockedUntil] = null
            } get Users.id
        }

        return Pair(generateToken(userId, username), userId)
    }

    /**
     * Authenticates a user with their credentials.
     * Tracks failed login attempts and locks the account after 3 consecutive failures.
     *
     * @param username The user's username
     * @param password The user's plaintext password
     * @return JWT token string on success
     * @throws IllegalArgumentException if credentials are invalid
     * @throws AccountLockedException if the account is currently locked
     */
    suspend fun login(username: String, password: String): Pair<String, Int> {
        val user = dbQuery {
            Users.selectAll().where { Users.username eq username }.singleOrNull()
        } ?: throw IllegalArgumentException("Invalid username or password")

        // Check if the account is currently locked
        val lockedUntil = user[Users.lockedUntil]
        if (lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil)) {
            val minutesRemaining = java.time.Duration.between(LocalDateTime.now(), lockedUntil).toMinutes() + 1
            throw AccountLockedException(minutesRemaining)
        }

        // If the lockout period has expired, reset the counter
        if (lockedUntil != null && !LocalDateTime.now().isBefore(lockedUntil)) {
            dbQuery {
                Users.update({ Users.id eq user[Users.id] }) {
                    it[Users.failedAttempts] = 0
                    it[Users.lockedUntil] = null
                }
            }
        }

        // Constant-time comparison via BCrypt.checkpw prevents timing attacks
        if (!BCrypt.checkpw(password, user[Users.passwordHash])) {
            // Increment failed attempts
            val newFailedAttempts = user[Users.failedAttempts] + 1

            dbQuery {
                Users.update({ Users.id eq user[Users.id] }) {
                    it[Users.failedAttempts] = newFailedAttempts
                    // Lock the account after MAX_FAILED_ATTEMPTS
                    if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
                        it[Users.lockedUntil] = LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES)
                    }
                }
            }

            if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
                throw AccountLockedException(LOCKOUT_DURATION_MINUTES)
            }

            val attemptsRemaining = MAX_FAILED_ATTEMPTS - newFailedAttempts
            throw IllegalArgumentException(
                "Incorrect username or password. $attemptsRemaining attempt(s) remaining before your account is locked."
            )
        }

        // Successful login — reset failed attempts counter
        dbQuery {
            Users.update({ Users.id eq user[Users.id] }) {
                it[Users.failedAttempts] = 0
                it[Users.lockedUntil] = null
            }
        }

        val userId = user[Users.id]
        return Pair(generateToken(userId, username), userId)
    }

    /**
     * Generates a signed JWT token with user claims.
     * Token expires after the configured duration (default: 24 hours).
     */
    private fun generateToken(userId: Int, username: String): String {
        return JWT.create()
            .withSubject("Authentication")
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + jwtExpiration))
            .sign(Algorithm.HMAC256(jwtSecret))
    }
}
