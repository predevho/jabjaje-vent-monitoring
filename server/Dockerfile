# Build context is the repository root (see docker-compose.yml).

# 1) React 화면 빌드 (ADR-0010)
FROM node:22-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

# 2) Spring Boot 빌드. 화면 빌드 결과를 정적 리소스로 넣어 주소 하나로 함께 내보낸다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle ./
COPY gradle gradle
COPY server/build.gradle server/build.gradle
# settings.gradle includes every module, so each module's build file must exist.
COPY simulator/build.gradle simulator/build.gradle
RUN ./gradlew --no-daemon :server:dependencies > /dev/null
COPY server/src server/src
COPY --from=web /web/dist server/src/main/resources/static
RUN ./gradlew --no-daemon :server:bootJar

# 3) 실행 이미지
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/server/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
