FROM gradle:8.10.2-jdk21 AS build

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradlew ./
COPY gradle/ gradle/

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

COPY src/ src/

RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 9003

ENTRYPOINT ["java", "-jar", "app.jar"]
