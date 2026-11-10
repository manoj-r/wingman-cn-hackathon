FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /workspace
COPY backend/ ./backend/
RUN chmod +x backend/gradlew \
    && ./backend/gradlew -p backend --no-daemon bootJar

FROM node:22-alpine AS web-build
WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN mkdir -p public \
    && NEXT_TELEMETRY_DISABLED=1 npm run build \
    && npm prune --omit=dev --no-audit --no-fund

FROM node:22-alpine AS runtime
RUN apk add --no-cache openjdk21-jre-headless
WORKDIR /app
COPY --from=backend-build --chown=node:node /workspace/backend/build/libs/*.jar /app/backend/app.jar
COPY --from=web-build --chown=node:node /app/web/package.json /app/web/next.config.ts /app/web/
COPY --from=web-build --chown=node:node /app/web/node_modules /app/web/node_modules
COPY --from=web-build --chown=node:node /app/web/.next /app/web/.next
COPY --from=web-build --chown=node:node /app/web/public /app/web/public
COPY --chown=node:node scripts/wingman-container-entrypoint.sh /usr/local/bin/wingman-entrypoint

ARG APP_VERSION=dev
ENV APP_VERSION=$APP_VERSION \
    NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    BACKEND_ADDRESS=127.0.0.1 \
    OTEL_SERVICE_NAME=conference-wingman-backend \
    OTEL_TRACES_EXPORTER=none

USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["/bin/sh", "/usr/local/bin/wingman-entrypoint"]
