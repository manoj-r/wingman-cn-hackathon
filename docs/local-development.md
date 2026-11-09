# Local development

The original `app/`, root Dockerfile, GitHub build workflow and Radar manifests remain unchanged. The new Java backend runs locally on loopback port 8081; PostgreSQL and Elasticsearch run through Compose. Frontend and networking features are subsequent milestones.

## Prerequisites

Use Java 21 (set `JAVA_HOME` to your JDK 21 installation), Python 3 for the optional password-generation command below, and Docker with Compose. Gradle 8.14.3 is downloaded automatically by the checked-in wrapper, with its official SHA-256 verified. Spring Boot is pinned to 3.5.16. Node 24 is installed on the current machine and meets Next.js 16's minimum Node 20.9 requirement; the frontend dependency lock is deferred to its feature branch.

On the current machine Java 21 is at `/Users/manoj/.sdkman/candidates/java/21.0.7-tem`. Commands below assume `JAVA_HOME` selects it. Confirm first:

```sh
java -version
docker compose version
```

## OpenTelemetry

The backend uses the OpenTelemetry SDK for explicit application spans and OTLP/HTTP export. Configure `OTEL_TRACES_EXPORTER=otlp`, `OTEL_EXPORTER_OTLP_ENDPOINT` (the full trace endpoint, typically ending in `/v1/traces`), and `OTEL_EXPORTER_OTLP_HEADERS` in the local environment. Keep endpoint credentials in ignored `.env` or another secret store. With the default exporter `none`, or without an endpoint, the backend runs without exporting. Export uses a bounded timeout and a background batch processor, so an unavailable collector does not fail registration. Registration spans record only the fixed operation and outcome; no attendee or conference data is added.

Dash0's Java integration recommends the OpenTelemetry Java agent (or its Kubernetes Operator) for automatic instrumentation. We retain explicit SDK instrumentation for this MVP because the agent's Spring MVC server spans include raw `url.path` and `url.query` attributes by default; this API puts conference and registration identifiers in paths. Its documented path exclusion/sampling controls do not redact those attributes while retaining the spans. Enabling the agent before an attribute-redaction mechanism is verified would send identifier-bearing URLs to the exporter. HTTP/JDBC automatic spans and provider/matching/introduction spans therefore remain out of scope for this privacy-preserving slice. Keep all OTLP headers private; do not paste them into commands captured in shell history.

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
