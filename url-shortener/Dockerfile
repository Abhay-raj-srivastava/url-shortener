# =============================================================
#  Multi-stage Dockerfile for URL Shortener Service
#
#  Stage 1 — Build (Maven + JDK 21)
#  Stage 2 — Runtime (JRE 21 slim)
#
#  Why multi-stage?
#  - The build stage contains Maven, the full JDK, and all
#    downloaded dependencies (~300 MB+). None of that belongs
#    in the production image.
#  - The final image contains only the JRE and the application
#    JAR, keeping it small and reducing the attack surface.
#
#  Layer caching strategy:
#  - pom.xml is copied and dependencies are resolved BEFORE
#    copying source code. Docker caches this layer so a source-
#    only change does not re-download all dependencies.
# =============================================================

# ─── Stage 1: Build ──────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copy Maven wrapper and POM first — cached unless pom.xml changes
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Now copy source and build
COPY src src

# Package, skip tests (tests run in CI before this stage)
RUN ./mvnw package -DskipTests -B

# Extract the layered JAR for optimised Docker layers
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ─── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy layered JAR contents in dependency-change-frequency order
# (dependencies change least often → copied first = best cache hit)
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

# Expose the application port
EXPOSE 8080

# Health check — Docker will mark the container unhealthy if
# the Actuator /health endpoint stops responding
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers:
# -XX:+UseContainerSupport  — respect cgroup CPU/memory limits
# -XX:MaxRAMPercentage=75   — use up to 75% of container memory for heap
# -XX:+UseG1GC              — G1 GC is well-suited for low-latency services
# -Djava.security.egd=...   — faster SecureRandom on Linux (avoids /dev/random blocking)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
