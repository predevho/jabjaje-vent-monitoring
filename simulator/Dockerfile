# Build context is the repository root (see docker-compose.yml).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle ./
COPY gradle gradle
COPY server/build.gradle server/build.gradle
COPY simulator/build.gradle simulator/build.gradle
RUN ./gradlew --no-daemon :simulator:dependencies > /dev/null
COPY simulator/src simulator/src
RUN ./gradlew --no-daemon :simulator:installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/simulator/build/install/simulator .
ENTRYPOINT ["/app/bin/simulator"]
