// =============================================================================
// SimuChat — Authentication Service Tests
// =============================================================================
// Unit tests for user registration, login, account lockout, and edge cases.
// Uses an H2 in-memory database for fast, isolated test execution.
// =============================================================================

package com.simuchat

import com.simuchat.services.AccountLockedException
import com.simuchat.services.AuthService
import io.ktor.server.config.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthServiceTest {

    private lateinit var authService: AuthService

    @Before
    fun setup() {
        TestDatabaseFactory.init()
        TestDatabaseFactory.reset()

        val config = MapApplicationConfig(
            "jwt.secret" to "test-secret-key-for-unit-tests-only",
            "jwt.issuer" to "simuchat",
            "jwt.audience" to "simuchat-users",
            "jwt.realm" to "SimuChat API",
            "jwt.expiration" to "86400000"
        )
        authService = AuthService(config)
    }

    // -------------------------------------------------------------------------
    // Registration Tests
    // -------------------------------------------------------------------------

    @Test
    fun registerWithValidCredentialsReturnsTokenAndUserId() {
        runBlocking {
            val (token, userId) = authService.register("testuser", "password123")

            assertNotNull(token, "Token should not be null")
            assertTrue(token.isNotBlank(), "Token should not be blank")
            assertTrue(userId > 0, "User ID should be positive")
        }
    }

    @Test
    fun registerWithDuplicateUsernameThrowsException() {
        runBlocking {
            authService.register("duplicate_user", "password123")

            val exception = assertFailsWith<IllegalArgumentException> {
                authService.register("duplicate_user", "password456")
            }
            assertTrue(exception.message!!.contains("already taken"),
                "Error message should indicate username is taken")
        }
    }

    @Test
    fun registerWithShortUsernameThrowsException() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                authService.register("ab", "password123")
            }
        }
    }

    @Test
    fun registerWithShortPasswordThrowsException() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                authService.register("validuser", "12345")
            }
        }
    }

    @Test
    fun registerWithEmptyUsernameThrowsException() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                authService.register("", "password123")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Login Tests
    // -------------------------------------------------------------------------

    @Test
    fun loginWithCorrectCredentialsReturnsToken() {
        runBlocking {
            authService.register("login_test", "password123")

            val (token, userId) = authService.login("login_test", "password123")
            assertNotNull(token, "Token should not be null")
            assertTrue(token.isNotBlank(), "Token should not be blank")
            assertTrue(userId > 0, "User ID should be positive")
        }
    }

    @Test
    fun loginWithWrongPasswordThrowsExceptionWithAttemptsRemaining() {
        runBlocking {
            authService.register("wrong_pw_test", "correctpassword")

            val exception = assertFailsWith<IllegalArgumentException> {
                authService.login("wrong_pw_test", "wrongpassword")
            }
            assertTrue(exception.message!!.contains("attempt"),
                "Error message should mention remaining attempts")
        }
    }

    @Test
    fun loginWithNonExistentUsernameThrowsException() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                authService.login("nonexistent_user", "password123")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Account Lockout Tests
    // -------------------------------------------------------------------------

    @Test
    fun accountLocksAfterThreeFailedLoginAttempts() {
        runBlocking {
            authService.register("lockout_test", "correctpassword")

            assertFailsWith<IllegalArgumentException> {
                authService.login("lockout_test", "wrong1")
            }
            assertFailsWith<IllegalArgumentException> {
                authService.login("lockout_test", "wrong2")
            }

            assertFailsWith<AccountLockedException> {
                authService.login("lockout_test", "wrong3")
            }
        }
    }

    @Test
    fun loginBlockedWhenAccountIsLockedEvenWithCorrectPassword() {
        runBlocking {
            authService.register("locked_correct_pw", "correctpassword")

            repeat(2) {
                try { authService.login("locked_correct_pw", "wrongpw") } catch (_: Exception) {}
            }
            try { authService.login("locked_correct_pw", "wrongpw") } catch (_: Exception) {}

            assertFailsWith<AccountLockedException> {
                authService.login("locked_correct_pw", "correctpassword")
            }
        }
    }

    @Test
    fun successfulLoginResetsFailedAttemptCounter() {
        runBlocking {
            authService.register("reset_test", "correctpassword")

            assertFailsWith<IllegalArgumentException> {
                authService.login("reset_test", "wrong1")
            }
            assertFailsWith<IllegalArgumentException> {
                authService.login("reset_test", "wrong2")
            }

            val (token, _) = authService.login("reset_test", "correctpassword")
            assertNotNull(token)

            assertFailsWith<IllegalArgumentException> {
                authService.login("reset_test", "wrong3")
            }
            assertFailsWith<IllegalArgumentException> {
                authService.login("reset_test", "wrong4")
            }
            assertFailsWith<AccountLockedException> {
                authService.login("reset_test", "wrong5")
            }
        }
    }

    // -------------------------------------------------------------------------
    // JWT Token Validation Tests
    // -------------------------------------------------------------------------

    @Test
    fun differentUsersGetDifferentTokens() {
        runBlocking {
            val (token1, _) = authService.register("user_one", "password123")
            val (token2, _) = authService.register("user_two", "password123")

            assertTrue(token1 != token2, "Different users should receive different tokens")
        }
    }

    @Test
    fun loginReturnsSameUserIdAsRegistration() {
        runBlocking {
            val (_, registeredId) = authService.register("id_check", "password123")
            val (_, loginId) = authService.login("id_check", "password123")

            assertEquals(registeredId, loginId, "Login should return the same user ID as registration")
        }
    }
}
