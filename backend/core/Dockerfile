# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy dependency descriptors first for better layer caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN mvn dependency:go-offline -B -Pdefault

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -Pdefault


FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd --system appuser && \
    useradd --system --gid appuser --no-create-home appuser

# Copy the jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Create uploads directory (in case local file storage is used)
RUN mkdir -p /app/uploads && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Spring Boot default port
EXPOSE 8081

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/v1/api/test/health || exit 1

# JVM tuning for containers — respects Fargate memory/CPU limits
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]