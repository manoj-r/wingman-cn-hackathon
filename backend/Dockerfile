FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/backend
COPY backend/ ./
RUN ./gradlew --no-daemon clean bootJar :otel-agent-extension:jar

RUN wget -q "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.33.1/opentelemetry-javaagent.jar" \
      -O /tmp/opentelemetry-javaagent.jar \
    && echo "e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149  /tmp/opentelemetry-javaagent.jar" | sha256sum -c -

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/app.jar /app/app.jar
COPY --from=build /workspace/backend/otel-agent-extension/build/libs/wingman-otel-agent-extension.jar /opt/otel/wingman-otel-agent-extension.jar
COPY --from=build /tmp/opentelemetry-javaagent.jar /opt/otel/opentelemetry-javaagent.jar

ENV BACKEND_ADDRESS=0.0.0.0 \
    PORT=8081 \
    OTEL_SERVICE_NAME=conference-wingman-backend \
    OTEL_TRACES_EXPORTER=none \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none \
    OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
    OTEL_EXPORTER_OTLP_TIMEOUT=5000 \
    OTEL_BSP_EXPORT_TIMEOUT=5000 \
    OTEL_PROPAGATORS=tracecontext \
    OTEL_INSTRUMENTATION_JDBC_EXPERIMENTAL_CAPTURE_QUERY_PARAMETERS=false \
    OTEL_INSTRUMENTATION_COMMON_DB_STATEMENT_SANITIZER_ENABLED=true

USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-javaagent:/opt/otel/opentelemetry-javaagent.jar", "-Dotel.javaagent.extensions=/opt/otel/wingman-otel-agent-extension.jar", "-jar", "/app/app.jar"]
