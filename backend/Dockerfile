# =============================================================================
# SimuChat Backend — Multi-Stage Dockerfile
# =============================================================================
# Stage 1: Build the application using Gradle
# Stage 2: Run with a lightweight JRE Alpine image
#
# Build:  docker build -t simuchat-backend .
# Run:    docker run -p 8080:8080 simuchat-backend
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build
# ---------------------------------------------------------------------------
FROM gradle:8-jdk17 AS build

WORKDIR /app

# Copy dependency manifests first for better Docker layer caching
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies (cached unless build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code and build the fat JAR
COPY src ./src
RUN gradle buildFatJar --no-daemon

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the fat JAR from the build stage
COPY --from=build /app/build/libs/simuchat-backend.jar ./app.jar

# Switch to non-root user
USER appuser

# Expose the API port
EXPOSE 8080

# Health check for container orchestrators
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
