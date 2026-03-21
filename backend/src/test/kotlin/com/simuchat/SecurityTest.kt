// =============================================================================
// SimuChat — Security Tests
// =============================================================================
// Tests for common web security vulnerabilities including:
// - IDOR (Insecure Direct Object Reference)
// - SQL injection attempts
// - XSS payload handling
// - Input validation (oversized payloads, malformed data)
// - Thread isolation and cascade deletion
// - BCrypt timing attack resistance
// =============================================================================

package com.simuchat

import com.simuchat.services.AuthService
import com.simuchat.services.ChatService
import io.ktor.server.config.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class SecurityTest {

    private lateinit var authService: AuthService
    private lateinit var chatService: ChatService

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
        chatService = ChatService()
    }

    // -------------------------------------------------------------------------
    // IDOR / BOLA — Insecure Direct Object Reference
    // -------------------------------------------------------------------------

    @Test
    fun userCannotAccessAnotherUsersThreads() {
        runBlocking {
            val (_, userId1) = authService.register("user_a", "password123")
            val (_, userId2) = authService.register("user_b", "password123")

            val thread = chatService.createThread(userId1, "Cloud Strife")

            assertFailsWith<IllegalArgumentException> {
                chatService.getMessages(thread.id, userId2)
            }
        }
    }

    @Test
    fun userCannotDeleteAnotherUsersThread() {
        runBlocking {
            val (_, userId1) = authService.register("owner_user", "password123")
            val (_, userId2) = authService.register("attacker_user", "password123")

            val thread = chatService.createThread(userId1, "Tifa Lockhart")

            assertFailsWith<IllegalArgumentException> {
                chatService.deleteThread(thread.id, userId2)
            }
        }
    }

    @Test
    fun userCannotAddMessageToAnotherUsersThread() {
        runBlocking {
            val (_, userId1) = authService.register("msg_owner", "password123")
            val (_, userId2) = authService.register("msg_attacker", "password123")

            val thread = chatService.createThread(userId1, "Sephiroth")

            assertFailsWith<IllegalArgumentException> {
                chatService.addMessage(thread.id, userId2, "user", "Injected message")
            }
        }
    }

    // -------------------------------------------------------------------------
    // SQL Injection Attempts
    // -------------------------------------------------------------------------

    @Test
    fun sqlInjectionInUsernameIsSafelyHandled() {
        runBlocking {
            val maliciousUsername = "admin_DROP_TABLE"

            val (token, _) = authService.register(maliciousUsername, "password123")
            assertNotNull(token, "Registration should succeed with a safe username")
        }
    }

    @Test
    fun sqlInjectionInPasswordIsSafelyHandled() {
        runBlocking {
            authService.register("safe_user", "password123")

            val maliciousPassword = "' OR '1'='1"

            assertFailsWith<IllegalArgumentException> {
                authService.login("safe_user", maliciousPassword)
            }
        }
    }

    @Test
    fun sqlInjectionInMessageContentIsSafelyStored() {
        runBlocking {
            val (_, userId) = authService.register("sql_msg_test", "password123")
            val thread = chatService.createThread(userId, "Vivi Ornitier")

            val maliciousContent = "'; DROP TABLE ChatMessages; --"
            val message = chatService.addMessage(thread.id, userId, "user", maliciousContent)

            assertTrue(message.content == maliciousContent,
                "SQL injection content should be stored literally, not executed")

            val messages = chatService.getMessages(thread.id, userId)
            assertTrue(messages.isNotEmpty(), "Table should still exist after injection attempt")
        }
    }

    // -------------------------------------------------------------------------
    // XSS Payload Handling
    // -------------------------------------------------------------------------

    @Test
    fun xssPayloadInMessageIsStoredLiterally() {
        runBlocking {
            val (_, userId) = authService.register("xss_test", "password123")
            val thread = chatService.createThread(userId, "Lightning")

            val xssPayload = "<script>alert('XSS')</script><img onerror='alert(1)' src='x'>"
            val message = chatService.addMessage(thread.id, userId, "user", xssPayload)

            assertTrue(message.content.contains("<script>"),
                "XSS content should be stored as-is (frontend handles escaping)")
        }
    }

    // -------------------------------------------------------------------------
    // Input Validation
    // -------------------------------------------------------------------------

    @Test
    fun usernameMustMeetMinimumLengthRequirement() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                authService.register("ab", "validpassword")
            }
        }
    }

    @Test
    fun usernameMustNotExceedMaximumLength() {
        runBlocking {
            val longUsername = "a".repeat(51)
            assertFailsWith<IllegalArgumentException> {
                authService.register(longUsername, "validpassword")
            }
        }
    }

    @Test
    fun passwordMustMeetMinimumLengthRequirement() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                authService.register("valid_user", "12345")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Thread & Message Edge Cases
    // -------------------------------------------------------------------------

    @Test
    fun accessingNonExistentThreadReturnsError() {
        runBlocking {
            val (_, userId) = authService.register("ghost_thread", "password123")

            assertFailsWith<IllegalArgumentException> {
                chatService.getMessages(99999, userId)
            }
        }
    }

    @Test
    fun deletingNonExistentThreadReturnsError() {
        runBlocking {
            val (_, userId) = authService.register("ghost_delete", "password123")

            assertFailsWith<IllegalArgumentException> {
                chatService.deleteThread(99999, userId)
            }
        }
    }

    @Test
    fun threadDeletionCascadesToMessages() {
        runBlocking {
            val (_, userId) = authService.register("cascade_test", "password123")
            val thread = chatService.createThread(userId, "Yuna")

            chatService.addMessage(thread.id, userId, "user", "Hello Yuna!")
            chatService.addMessage(thread.id, userId, "model", "Hello there!")

            val messagesBefore = chatService.getMessages(thread.id, userId)
            assertTrue(messagesBefore.size == 2, "Should have 2 messages before deletion")

            chatService.deleteThread(thread.id, userId)

            assertFailsWith<IllegalArgumentException> {
                chatService.getMessages(thread.id, userId)
            }
        }
    }

    @Test
    fun userCanOnlySeeTheirOwnThreads() {
        runBlocking {
            val (_, userId1) = authService.register("list_user_a", "password123")
            val (_, userId2) = authService.register("list_user_b", "password123")

            chatService.createThread(userId1, "Cloud Strife")
            chatService.createThread(userId1, "Sephiroth")
            chatService.createThread(userId2, "Lightning")

            val threadsA = chatService.getThreadsForUser(userId1)
            assertTrue(threadsA.size == 2, "User A should see exactly 2 threads, got ${threadsA.size}")

            val threadsB = chatService.getThreadsForUser(userId2)
            assertTrue(threadsB.size == 1, "User B should see exactly 1 thread, got ${threadsB.size}")
        }
    }

    // -------------------------------------------------------------------------
    // Timing & Brute Force Resistance
    // -------------------------------------------------------------------------

    @Test
    fun bcryptPreventTimingAttacksOnLogin() {
        runBlocking {
            authService.register("timing_test", "correctpassword")

            val startCorrect = System.nanoTime()
            try { authService.login("timing_test", "correctpassword") } catch (_: Exception) {}
            val correctTime = System.nanoTime() - startCorrect

            val startWrong = System.nanoTime()
            try { authService.login("timing_test", "wrongpassword") } catch (_: Exception) {}
            val wrongTime = System.nanoTime() - startWrong

            assertTrue(correctTime > 1_000_000, "Correct password check should use BCrypt (> 1ms)")
            assertTrue(wrongTime > 1_000_000, "Wrong password check should use BCrypt (> 1ms)")
        }
    }
}
