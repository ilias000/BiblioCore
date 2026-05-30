# syntax=docker/dockerfile:1

# ====================================================================
# Stage 1 — build
# Temurin JDK 25 on Alpine. Builds with the project's Maven Wrapper
# (./mvnw) so the Maven version is pinned and we don't depend on a
# Maven base-image tag. pom.xml + wrapper are copied first so the
# dependency layer is cached and only re-resolves when the pom changes.
# ====================================================================
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# Wrapper + pom first (cached dependency layer).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Build the application. Tests run in CI (`mvn clean verify`), so the
# image build skips them for fast, reproducible packaging.
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ====================================================================
# Stage 2 — runtime
# Slim JRE-only Alpine image, non-root user, profile preset to docker-pg.
# ====================================================================
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker-pg \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]