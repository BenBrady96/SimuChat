// =============================================================================
// SimuChat Backend — Gradle Build Configuration
// =============================================================================
// Kotlin/Ktor REST API with JWT authentication, Exposed ORM, HikariCP
// connection pooling, BCrypt password hashing, and Gemini AI integration.
// =============================================================================

val ktorVersion = "2.3.12"
val exposedVersion = "0.52.0"
val hikariVersion = "5.1.0"
val postgresVersion = "42.7.3"
val logbackVersion = "1.5.6"

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    id("io.ktor.plugin") version "2.3.12"
    application
}

group = "com.simuchat"
version = "1.0.0"

application {
    mainClass.set("com.simuchat.ApplicationKt")
}

// Configure Ktor's Shadow JAR plugin for creating a fat JAR
ktor {
    fatJar {
        archiveFileName.set("simuchat-backend.jar")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Ktor Server ---
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")

    // --- Ktor Client (for Gemini API calls) ---
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // --- Database ---
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")

    // --- Security ---
    implementation("org.mindrot:jbcrypt:0.4")

    // --- Logging ---
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // --- Testing ---
    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.25")
    testImplementation("com.h2database:h2:2.2.224") // In-memory DB for tests
}
