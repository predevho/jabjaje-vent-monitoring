# Build context is the repository root (see docker-compose.yml).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle ./
COPY gradle gradle
COPY server/build.gradle server/build.gradle
RUN ./gradlew --no-daemon :server:dependencies > /dev/null
COPY server/src server/src
RUN ./gradlew --no-daemon :server:bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/server/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
