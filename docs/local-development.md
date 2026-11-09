# Local development

The original `app/`, root Dockerfile, GitHub build workflow and Radar manifests remain unchanged. The Java backend runs locally on loopback port 8081; PostgreSQL and Elasticsearch run through Compose. `backend/Dockerfile` builds a separate Java backend image with a pinned OpenTelemetry Java agent. Frontend and networking features are subsequent milestones.

## Prerequisites

Use Java 21 (set `JAVA_HOME` to your JDK 21 installation), Python 3 for the optional password-generation command below, and Docker with Compose. Gradle 8.14.3 is downloaded automatically by the checked-in wrapper, with its official SHA-256 verified. Spring Boot is pinned to 3.5.16. Node 24 is installed on the current machine and meets Next.js 16's minimum Node 20.9 requirement; the frontend dependency lock is deferred to its feature branch.

On the current machine Java 21 is at `/Users/manoj/.sdkman/candidates/java/21.0.7-tem`. Commands below assume `JAVA_HOME` selects it. Confirm first:

```sh
java -version
docker compose version
```

## OpenTelemetry

The backend container uses the OpenTelemetry Java agent for HTTP and JDBC instrumentation and retains one explicit registration span. The agent is the only SDK/exporter; application code uses its OpenTelemetry API. OTLP traces, metrics, and logs default to disabled. To opt into traces, set `OTEL_TRACES_EXPORTER=otlp`, `OTEL_EXPORTER_OTLP_ENDPOINT` to the Dash0 OTLP/HTTP base endpoint, `OTEL_EXPORTER_OTLP_HEADERS` in a private environment, and optionally `OTEL_SERVICE_NAME` (defaults to `conference-wingman-backend`). The exporter appends `/v1/traces` to the base endpoint. The agent's batch exporter is asynchronous with a 5-second export timeout, so an unavailable collector does not block registration.

Before export, the bundled agent extension allowlists span and resource attributes, rewrites automatic span names to fixed labels, and strips event/link attributes and status descriptions. It removes raw URL paths, query strings, full URLs, headers, SQL statements/parameters, prompts, vectors, and unrecognized attributes. It retains only known HTTP methods/statuses, the registered route templates, PostgreSQL operation names, the service identity, and the fixed registration operation/outcome. Baggage propagation is disabled. Focused exporter tests assert private sample values do not reach the delegate exporter.

The backend runtime image is built separately from the current Node starter image:

```sh
docker build -f backend/Dockerfile -t wingman-backend:local .
```

The image pins OpenTelemetry Java agent `2.33.1` and checks SHA-256 `e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149` while building. It starts Java with `-javaagent` and loads the privacy extension. The current `deploy/` manifests still target the Node starter; the example below shows the Secret and environment mapping to use when the Java backend image is selected for a workload.

Create this Secret through Radar in namespace `team-wingman`, replacing the placeholder privately. Do not commit a populated Secret manifest:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: dash0-otel
  namespace: team-wingman
type: Opaque
stringData:
  OTEL_EXPORTER_OTLP_HEADERS: "Authorization=Bearer <DASH0_TOKEN>"
```

Map the Secret and Dash0 endpoint on the Java backend container:

```yaml
env:
  - name: OTEL_TRACES_EXPORTER
    value: otlp
  - name: OTEL_EXPORTER_OTLP_ENDPOINT
    value: "<DASH0_OTLP_HTTP_BASE_ENDPOINT>"
  - name: OTEL_EXPORTER_OTLP_HEADERS
    valueFrom:
      secretKeyRef:
        name: dash0-otel
        key: OTEL_EXPORTER_OTLP_HEADERS
  - name: OTEL_SERVICE_NAME
    value: conference-wingman-backend
```

Keep the exporter endpoint and authorization header out of command-line arguments. The header is read from the Secret as an environment variable; the service name and endpoint are ordinary runtime configuration.

## Local dependencies

Copy `.env.example` to ignored `.env` and fill `DATABASE_PASSWORD` with a private random value. Plain `KEY=value` format is supported by both Compose and Spring; use URL-safe password characters. Never commit `.env`. Alternatively, this command creates a minimal password-only file and refuses to overwrite one:

```sh
python3 -c 'from pathlib import Path; import secrets; p=Path(".env"); p.open("x").write("DATABASE_PASSWORD="+secrets.token_urlsafe(32)+"\n"); p.chmod(0o600)'
docker compose config --quiet
docker compose up -d postgres elasticsearch
docker compose ps
```

Ports 5432 and 9200 must be free. Both bind only to 127.0.0.1. Elasticsearch security is disabled solely for this loopback-only development service. Services have named persistent volumes and memory limits totaling 2 GiB. Changing the password file after database initialization does not change the existing PostgreSQL user's password. Keep the original value with that volume.

## Backend

Create the dedicated integration-test database once after PostgreSQL is healthy (an already-existing database needs no change):

```sh
docker compose exec -T postgres createdb -U wingman wingman_test
```

Tests use `wingman_test`, never reset demo data, and leave uniquely identified fictional fixtures there. Flyway creates the schema; JPA validates it on startup. The restart test closes and starts the application against the same database to verify persisted registrations and counts.

```sh
./backend/gradlew -p backend test
./backend/gradlew -p backend bootRun
```

Spring imports the ignored root `.env` as a properties file. Stop the backend with Ctrl-C. From a second terminal:

```sh
curl -f http://localhost:8081/healthz
curl -f http://localhost:8081/readyz
curl -f http://localhost:8081/api/v1/version
curl -f http://localhost:9200/_cluster/health
```

Liveness is independent of database and AI availability after startup. Startup requires PostgreSQL for Flyway and JPA schema validation. Readiness includes PostgreSQL connectivity and returns 503 if it is unavailable. Health integration tests use the dedicated PostgreSQL test database. `APP_VERSION` supplies the version response, defaulting to `local`.

M2 adds `/api/v1/conferences`, private organizer management, and registration. `PUBLIC_APP_URL` supplies the trusted registration-link base (default `http://localhost:3000`); HTTPS enables Secure session cookies. Save the returned organizer token privately: only its digest is stored. Matching routes are deferred to M3/M4; M2 authenticates attendee capabilities without returning fabricated matches.

```sh
docker compose stop
```

Stopping preserves data. Do not remove volumes to resolve routine startup problems. No cluster deployment is part of this setup.

## Provider prerequisites still open

On 2026-11-09, minimal real calls using the existing OpenAI environment credential returned HTTP 429 for both `text-embedding-3-small` (1536 requested dimensions) and `gpt-4.1-mini`. No successful embedding or generation is claimed. Ollama's local server was not reachable. Dash0 OTLP endpoint/auth were absent from the current environment. Resolve these privately before accepting real-AI and telemetry milestones. No credentials, provider response bodies or profile text were logged by the probes.
