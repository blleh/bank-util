# Multi-stage Dockerfile for BankMapper Spring Boot Application

# Stage 1: Build stage
FROM openjdk:19-jdk-slim as builder

# Set working directory
WORKDIR /app

# Copy Maven configuration files
COPY pom.xml .

# Copy source code
COPY src ./src

# Install Maven
RUN apt-get update && apt-get install -y maven

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM openjdk:19-jre-slim

# Set working directory
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r bankmapper && useradd -r -g bankmapper bankmapper

# Copy the JAR file from the builder stage
COPY --from=builder /app/target/BankMapper-1.0-SNAPSHOT.jar app.jar

# Create directories for input and output files
RUN mkdir -p /app/input /app/output && \
    chown -R bankmapper:bankmapper /app

# Switch to non-root user
USER bankmapper

# Expose the port that Spring Boot runs on
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Default JVM arguments for containerized environment
CMD ["--server.port=8080"]
