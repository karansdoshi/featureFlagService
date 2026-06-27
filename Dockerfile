# syntax=docker/dockerfile:1

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Cache dependencies first.
COPY pom.xml .
RUN mvn -q -B -e dependency:go-offline
COPY src ./src
RUN mvn -q -B -e -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user; pre-create the H2 data dir and hand /app to that user
# so the file DB can be created at runtime.
RUN useradd --system --uid 10001 --no-create-home appuser \
    && mkdir -p /app/data
COPY --from=build /build/target/feature-flag-service-*.jar app.jar
RUN chown -R appuser /app
USER appuser

EXPOSE 8080
ENV JAVA_OPTS=""
# H2 file DB lives under /app/data; mount a volume to persist it.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
