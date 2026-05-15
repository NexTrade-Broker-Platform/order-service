# Multi-stage build for Spring Boot application
FROM gradle:8.10.2-jdk21 AS build

WORKDIR /app

# Copy source code
COPY . .

# Build the application
RUN gradle clean bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 9003

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

