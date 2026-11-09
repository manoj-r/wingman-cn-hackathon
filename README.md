# Conference Wingman

Conference Wingman helps attendees find people to connect with at a conference. The local application is a Next.js frontend backed by a Java 21/Spring Boot API and PostgreSQL. Elasticsearch, an embedding/LLM provider, and Dash0 are used by the matching and observability feature work when those integrations are present.

## What works on `main`

The current `main` branch contains conference creation, organizer access, and attendee registration. Create a conference at `/conferences/new`, share its registration link, and attendees can register. Organizers can re-enter the one-time organizer token at `/organizer` to see the registration count. The `/connections` page currently confirms registration; recommendations and introductions are later feature work and are not part of this branch's local flow.

Semantic matching, recommendations, and OpenTelemetry have implementation work in dependent feature branches, but are not integrated into `main` yet. A PR merged into another feature branch does not make that code available from `main`.

The repository also retains the original Node starter as the application deployed through the current Radar GitOps path. The local Java/Next.js application is not yet the application served at the public Radar URL. See [Radar Cloud](#radar-cloud-and-gitops) before treating a healthy cluster deployment as proof of the Wingman flow.

## Prerequisites

- Git
- Java 21
- Node.js 22 or newer and npm
- Docker with Docker Compose v2, with about 2 GiB available for PostgreSQL and Elasticsearch
- Ports `3000`, `8081`, `5432`, and `9200` available for the local stack

## Run locally

From the repository root, copy the example environment file and set a private database password in `.env`:

```sh
cp .env.example .env
```

`.env` is ignored by Git. Keep the same `DATABASE_PASSWORD` while reusing the persistent PostgreSQL volume. The local Elasticsearch service has security disabled and binds only to loopback; do not expose it to a network.

Start PostgreSQL and Elasticsearch:

```sh
docker compose config --quiet
docker compose up -d postgres elasticsearch
docker compose ps
```

The first backend test run needs a separate test database. Create it once after PostgreSQL is healthy:

```sh
docker compose exec -T postgres createdb -U wingman wingman_test
```

Run the backend tests from the repository root:

```sh
./backend/gradlew -p backend test
```

Start the backend in terminal 1:

```sh
./backend/gradlew -p backend bootRun
```

Install the frontend dependencies and start the UI in terminal 2:

```sh
cd web
npm ci
npm run dev
```

Open <http://127.0.0.1:3000>. Create a conference, register an attendee using the generated link, then open `/organizer` to check the attendee count. The organizer token is shown once when the conference is created; keep it private. The browser receives an HttpOnly attendee session cookie after registration.

The backend reads the root `.env` file and listens on `127.0.0.1:8081`. Check its health endpoints in another terminal:

```sh
curl -f http://127.0.0.1:8081/healthz
curl -f http://127.0.0.1:8081/readyz
curl -f http://127.0.0.1:8081/api/v1/version
```

Stop the frontend and backend with Ctrl-C. Stop dependency containers while preserving their data with:

```sh
docker compose stop
```

Do not use `docker compose down -v` to troubleshoot; `-v` deletes the local database and search data.

## Test and verify

Backend tests use PostgreSQL and the dedicated `wingman_test` database described above. The tests leave uniquely named fictional records in that database.

From the repository root:

```sh
./backend/gradlew -p backend test
```

The frontend provides these checks (there is no configured lint script):

```sh
cd web
npm run typecheck
npm run build
npm run test:e2e
```

The Playwright end-to-end suite starts a local contract fixture server; it tests the UI and API-proxy behavior, not the live PostgreSQL backend or external providers. For a live smoke test, start both services as above and complete conference creation and attendee registration in the browser.

## Optional provider configuration

The ignored `.env.example` lists the provider settings used by the matching and telemetry implementation work. These settings only take effect when running a branch that includes the corresponding integration; they do not enable recommendations on the current `main` branch. Provider availability and credentials must be verified separately.

| Integration | Environment settings |
|---|---|
| Embeddings | `EMBEDDING_PROVIDER`, `EMBEDDING_BASE_URL`, `EMBEDDING_API_KEY`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS` |
| LLM generation | `LLM_PROVIDER`, `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` |
| Elastic Cloud | `ELASTICSEARCH_URL`, `ELASTICSEARCH_API_KEY` |
| Dash0 / OTLP | `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORTER_OTLP_HEADERS`, `OTEL_EXPORTER_OTLP_PROTOCOL`; traces must also be enabled with `OTEL_TRACES_EXPORTER=otlp` in the agent-enabled backend |

For local development, put credentials only in the ignored `.env`. On Radar, create a Kubernetes Secret through Radar Cloud and reference its keys from the workload environment. A Secret by itself does not configure the application: the deployment must include the matching `secretKeyRef`, and the currently deployed Node starter does not consume these provider variables. Use the environment names shown above; never use a browser-exposed key.

The Dash0 endpoint is ordinary configuration. Keep the authorization header/token in a Secret. Elasticsearch and AI credentials are separate secrets. Do not put real values in example YAML, source, image build arguments, logs, commits, pull requests, or chat.

## Radar Cloud and GitOps

The team cluster is named **`wingman`** and the application namespace is **`team-wingman`**. Cluster changes and observations for this project use Radar Cloud; do not target `cnh` or another team's cluster. The configured public URL is <https://wingman.cnh.radarhq.io>.

The active Argo CD source is `argocd/application.yaml`: Application `app` in namespace `team-wingman`, reading the `deploy/` directory from `main`. After an intentional application/deployment change is merged and pushed to `main`, the GitHub `build-and-deploy` workflow builds the root image and updates the image tag in `deploy/kustomization.yaml`; Argo CD then syncs that directory. Markdown-only changes do not trigger an image build.

**Current deployment limitation:** the root `Dockerfile` builds `app/`, the Node starter. The current `deploy/` manifests and image workflow do not build or route traffic to `web/` or `backend/`. Therefore the public URL is not yet the locally runnable Conference Wingman UI/API. Do not treat the starter's health or version response as verification of attendee registration, matching, or recommendations. A separate reviewed image and routing migration is needed before those features can be tested through the public URL.

`deploy/` is the active Kustomize/GitOps input. A Helm chart is optional packaging and does not change the current Argo CD source path. Keep the active Service `app` on port 80 when planning a future routing migration.

### Create or update a Radar Secret

In Radar Cloud, select the `wingman` cluster, open **Resources → Secrets**, and create the Secret in namespace `team-wingman`. Enter values directly in Radar's editor; do not save a populated Secret manifest in the repository. Example shape (replace placeholders only in Radar):

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: wingman-provider-credentials
  namespace: team-wingman
type: Opaque
stringData:
  LLM_API_KEY: "<provider-key>"
  EMBEDDING_API_KEY: "<provider-key>"
  ELASTICSEARCH_API_KEY: "<elastic-api-key>"
  OTEL_EXPORTER_OTLP_HEADERS: "Authorization=Bearer <dash0-token>"
```

Create only keys the selected integration needs. Commit references to Secret names and keys only when the corresponding workload is configured to read them. Argo CD does not create a Secret just because a manifest references it, and changing a Secret consumed as environment variables may require a workload restart.

For cluster state and rollout checks, use Radar Cloud and the repository's [agent workflow notes](examples/agent-workflows/README.md). Before calling a deployment live, verify the pushed revision, Argo sync, deployed image, workload health, and the public endpoint's behavior.

## Troubleshooting

- **Compose refuses to start:** check that `.env` has a non-empty `DATABASE_PASSWORD`, Docker is running, and ports `5432`/`9200` are available.
- **Backend fails during startup:** confirm PostgreSQL is healthy with `docker compose ps`; confirm `.env` has the database password used when the persistent volume was first initialized.
- **Backend readiness returns 503:** check PostgreSQL connectivity. `/readyz` includes the database; `/healthz` is liveness only.
- **Frontend reports that the service is unavailable:** confirm the backend is running at `127.0.0.1:8081`; the Next.js API rewrite defaults to that address.
- **Playwright cannot launch a browser:** install the browser required by the Playwright version in `web/package-lock.json` with `npx playwright install chromium`.
- **Radar URL works but Wingman pages do not:** the current cluster image is the Node starter. The local Java/Next.js app has not been migrated into `deploy/` and the root image build.

## Project notes

- Product requirements and implementation status: [`docs/specs/`](docs/specs/)
- Detailed local backend setup: [`docs/local-development.md`](docs/local-development.md)
- Secret, dependency, and one-off Job procedures: [`examples/agent-workflows/README.md`](examples/agent-workflows/README.md)
- `app/` and the root `Dockerfile` are retained as the current Radar-deployed starter while the local Wingman application is developed separately.
