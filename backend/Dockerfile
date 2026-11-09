# Build from the repository root: docker build -f backend/Dockerfile .
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY backend/ .
RUN sh ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/*-0.1.0.jar /app/backend.jar
ARG APP_VERSION=dev
ENV PORT=8080 BACKEND_ADDRESS=0.0.0.0 APP_VERSION=$APP_VERSION
USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
