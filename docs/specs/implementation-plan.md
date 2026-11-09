# Conference Wingman implementation plan

Status: proposed checklist, not evidence of implementation. Execute only after this specification handoff. All paths below other than existing starter paths are proposed. Commands must be run one at a time from the repository root unless marked with a working directory. Test selectors name tests to create during that milestone; they do not exist yet.

**Goal:** deliver a reliable local end-to-end application and a reviewed three-minute video before 17:30 Mountain.

**Architecture:** additive Next.js frontend and Java 21 Spring Boot backend, PostgreSQL source of truth, Elasticsearch vector projection, configurable real AI providers and OTLP to Dash0. Preserve the existing Node/Radar GitOps starter.

**Tech stack:** TypeScript, Tailwind, Java 21, Gradle wrapper, Spring Data JPA, Flyway, PostgreSQL, Elasticsearch and OpenTelemetry.

## Scheduling and execution rules

Planning time observed about 12:14 Mountain on 2026-11-09, leaving roughly five hours before submission. Estimates below total 235–285 engineering minutes plus a 30-minute recording/submission reserve; upper estimates exceed the available window. Time-box unknowns immediately. Target feature freeze at 16:45, recording complete by 17:10, submission by 17:20. Recalculate against actual clock at execution start.

Work serially in small verified commits. For each milestone, write the named meaningful test first, observe its failure, implement the smallest slice, then run the listed checks and review the diff. Do not modify `.idea/`. Do not accidentally stage secrets or generated artifacts. New dependencies need current Context7 documentation and pinned compatible versions. Check original starter still answers `/healthz`, `/readyz` and `/api/sessions` after introducing build changes.

Critical path: M0 → M1 → M2 → M3 → M4 → M6 → M8. M5 can follow M2 before M3 if visual progress is useful; M7 must complete before M8 acceptance. No implementation is part of this specification task.

## Milestones

### M0 — Resolve prerequisites and lock the runtime contract

- [ ] Complete M0.
- Scope: check tools, memory, Docker, real AI connectivity, Dash0 access and compatible dependency versions; record blockers before scaffolding. Do not print environment values or credential files.
- Files/components affected: future `.env.example`, `docs/specs/architecture.md` dependency decisions only; inspect existing Docker/workflow without rewriting.
- Acceptance criteria: Java 21, Node 22, Docker Compose usable; embedding probe confirms actual model dimension; short LLM request succeeds; Dash0 endpoint/auth available; exact versions chosen from current docs. Ollama selected only if already practical. Missing credentials explicitly block affected FRs.
- Verification commands: `java -version`; `node --version`; `docker compose version`; `docker info`; Context7 library then docs commands for chosen Spring Boot, Next.js, provider and telemetry configuration. Provider checks use a private probe reading environment, never credentials on command line.
- Dependencies: none; user-supplied private credentials where needed.
- Estimated effort: 15–20 minutes. Stop investigating an unavailable provider after 10 minutes and record the blocker; use an available supported provider if authorized credentials exist.

### M1 — Add isolated backend and local dependencies

- [ ] Complete M1.
- Scope: scaffold Java backend with wrapper, Compose PostgreSQL/Elasticsearch, health/version endpoints and environment-based configuration. Preserve root starter build.
- Files/components affected: `backend/build.gradle`, `backend/settings.gradle`, `backend/gradlew`, `backend/gradle/wrapper/`, `backend/src/main/java/io/conferencewingman/WingmanApplication.java`, `backend/src/main/resources/application.yml`, `compose.yaml`, `.env.example`, `.gitignore` additions for Gradle/Next output; `backend/src/test/java/io/conferencewingman/HealthIntegrationTest.java`.
- Acceptance criteria: dependencies healthy; backend starts on 8081; health/version returns JSON or starter-compatible health text; no passwords committed; containers bind dependency ports to loopback and persist named volumes.
- Verification commands: `docker compose config --quiet`; `docker compose up -d postgres elasticsearch`; `docker compose ps`; `./backend/gradlew -p backend test --tests '*HealthIntegrationTest'`; `./backend/gradlew -p backend bootRun`; in another terminal `curl -f http://localhost:8081/healthz`.
- Dependencies: M0, available container images and tool downloads.
- Estimated effort: 20–25 minutes.

### M2 — Persist conferences and secure registration

- [ ] Complete M2.
- Scope: migrations, JPA entities, validation, organizer token, registration transaction, attendee session, duplicate/idempotent retry behavior and attendee count API. Store idempotency key/digest on registration; verify concurrent identical submissions create one registration and changed-payload replay returns 409.
- Files/components affected: `backend/src/main/resources/db/migration/V1__core_schema.sql`, backend `conference/`, `attendee/`, `registration/`, `security/`; tests `ConferenceApiIntegrationTest.java`, `RegistrationIntegrationTest.java`, `CapabilitySecurityTest.java` under backend test package.
- Acceptance criteria: FR-001/003/004; create/read/count works; bad date rejected; duplicate/replayed requests behave per API contract; concurrent duplicate insert cannot orphan attendee; wrong organizer token and absent attendee cookie denied; public JSON never exposes email/token hash. Committed registration survives restart.
- Verification commands: `./backend/gradlew -p backend test --tests '*ConferenceApiIntegrationTest' --tests '*RegistrationIntegrationTest' --tests '*CapabilitySecurityTest'`; restart backend; rerun API persistence assertions against real PostgreSQL. Integration tests use a dedicated test database, not destructive resets of demo data.
- Dependencies: M1.
- Estimated effort: 35–40 minutes.

### M3 — Real embeddings, durable indexing and filtered kNN

- [ ] Complete M3.
- Scope: embedding adapter, versioned mapping, durable worker/leases/retries, idempotent upsert and conference-filtered search.
- Files/components affected: `backend/src/main/resources/db/migration/V2__matching_state.sql`; backend `ai/EmbeddingClient.java`, `matching/ProfileIndexer.java`, `matching/MatchingWorker.java`, `matching/MatchSearch.java`; tests `SemanticSearchIntegrationTest.java`, `MatchingRecoveryTest.java`.
- Acceptance criteria: FR-005/006/007; real vectors indexed; three unique same-conference peers; self excluded; deliberately similar other-conference attendee excluded; 0/1/2 peers handled; dimension errors fail safely; Elasticsearch outage preserves registration; restart recovers pending/expired work. Stubs do not pass real-provider smoke gate.
- Verification commands: `./backend/gradlew -p backend test --tests '*SemanticSearchIntegrationTest' --tests '*MatchingRecoveryTest'`; `./backend/gradlew -p backend integrationTest` (add this task for opt-in real PostgreSQL/ES/provider tests). Inspect sanitized result IDs/counts through API, not raw profile dumps.
- Dependencies: M2, real embedding provider from M0, available Elasticsearch.
- Estimated effort: 35–40 minutes.

### M4 — Grounded explanations and introduction generation

- [ ] Complete M4.
- Scope: one batch LLM call for three explanations/openers, strict output validation, stored match snapshots, poll/retry endpoints and copy-only introduction endpoint.
- Files/components affected: backend `ai/LlmClient.java`, `matching/RecommendationService.java`, `matching/MatchController.java`, `matching/IntroductionService.java`; tests `RecommendationServiceTest.java`, `IntroductionSecurityTest.java`.
- Acceptance criteria: FR-008/009/010; real output references actual supplied interests/goals; invalid JSON/unknown IDs rejected; prompt injection text cannot grant access or alter tool behavior; wrong conference/nonmatch target denied; failure UI can distinguish templates from actual LLM output. Timeouts leave durable status, not an indefinite worker lease.
- Verification commands: `./backend/gradlew -p backend test --tests '*RecommendationServiceTest' --tests '*IntroductionSecurityTest'`; `./backend/gradlew -p backend integrationTest`; manually read one real explanation/opener/introduction for grounding and tone.
- Dependencies: M3, usable LLM credentials/model.
- Estimated effort: 25–30 minutes.

### M5 — Organizer and registration interface

- [ ] Complete M5.
- Scope: additive Next.js/TypeScript/Tailwind app, API proxy, create conference, private token handling, local QR rendering, attendee form and organizer count view.
- Files/components affected: `web/package.json`, lockfile, `web/next.config.ts`, `web/src/app/`, `web/src/components/`, `web/src/lib/api.ts`, `web/tests/organizer-registration.spec.ts`. Preserve `app/`.
- Acceptance criteria: FR-002 plus UI for FR-001/003; QR decodes to token-free registration URL; token shown once and privately re-enterable; public URLs have no capability; cookie passes proxy; validation/loading/error states usable on mobile. No provider key in browser bundle.
- Verification commands (working directory `web`): `npm ci`; `npm run lint`; `npm run build`; `npm run dev`; `npm run test:e2e -- organizer-registration.spec.ts`. Define these scripts with the chosen supported tooling. QR test decodes rendered output, rather than asserting the input string only.
- Dependencies: M2 and M0 version decisions.
- Estimated effort: 35–40 minutes.

### M6 — Recommendation cards and connection interaction

- [ ] Complete M6.
- Scope: poll states, three cards, explanation/opener, retry and editable/copyable introduction. No outgoing messages.
- Files/components affected: `web/src/app/connections/page.tsx`, `web/src/components/MatchCard.tsx`, `web/src/components/IntroductionDialog.tsx`, `web/tests/connections.spec.ts`.
- Acceptance criteria: successful registration reaches recommendations; pending, empty, failed and degraded states clear; polling stops at deadline; copy button works; keyboard dialog close/focus works; 375px layout fits; emails absent from network response and rendered cards.
- Verification commands (working directory `web`): `npm run test:e2e -- connections.spec.ts`; `npm run build`; browser walkthrough at 375px and 1440px against real backend.
- Dependencies: M4 and M5.
- Estimated effort: 20–25 minutes.

### M7 — End-to-end telemetry

- [ ] Complete M7.
- Scope: agent startup, explicit domain spans, OTLP exporter configuration and safe error correlation.
- Files/components affected: backend `observability/`, `scripts/run-backend.sh`, `.env.example` names only, `docs/local-development.md` startup instructions; tests `TelemetryPrivacyTest.java`.
- Acceptance criteria: FR-011/012; new registration trace includes embedding/search/LLM spans in Dash0; introduction creates a trace; provider failure marked; no emails, tokens, prompts or authorization values exported; unavailable exporter does not fail registration.
- Verification commands: `./backend/gradlew -p backend test --tests '*TelemetryPrivacyTest'`; `./scripts/run-backend.sh`; perform real registration and locate its trace in Dash0. Save a redacted screenshot for video; do not commit private exporter headers.
- Dependencies: M1 instrumentation choice; M4 for full trace; Dash0 credentials from M0.
- Estimated effort: 20–25 minutes.

### M8 — Reproducible seed, rehearsal and recording

- [ ] Complete M8 and submit.
- Scope: idempotent fictional fixtures, private seed/reindex CLI, documented clean startup, cross-conference smoke test, full rehearsal and three-minute recording.
- Files/components affected: `backend/src/main/resources/demo/attendees.json`, backend `demo/`, `scripts/demo-smoke.mjs`, `docs/local-development.md`, `docs/specs/demo-plan.md`, optional `docs/demo-evidence.md` with sanitized results only.
- Acceptance criteria: fresh database startup documented; seed twice leaves count unchanged; recommendations are ready before recording; full journey plus restart/isolation/privacy checks pass; video duration at most 3:00, readable UI and no secrets; working app and video submitted before deadline.
- Verification commands: `docker compose up -d postgres elasticsearch`; `./backend/gradlew -p backend bootRun --args='--spring.profiles.active=demo --wingman.demo.seed=true'`; `node scripts/demo-smoke.mjs`; `./backend/gradlew -p backend test integrationTest`; working directory `web`: `npm run lint`, `npm run build`, `npm run test:e2e`. Follow documented startup in two app terminals, never assume Compose launches host apps.
- Dependencies: M3, M6, M7; recording software and submission destination available.
- Estimated effort: 30–40 minutes engineering plus protected 30 minutes recording/submission reserve.

## Optional deployment gate — outside the core estimate

- [ ] Only if the local demo is already stable and time remains: plan the unified frontend/backend images and routing while retaining Service `app` port 80, health and version semantics. Add PostgreSQL via existing CloudNativePG example only if using cluster persistence; estimate Elasticsearch resource needs before committing manifests. Read the ship skill and use Radar for cluster state. No cluster deployment should be implied by these local milestones.
- Files/components affected: a separately reviewed change to root `Dockerfile`, workflow, `deploy/` and application routing.
- Acceptance/verification: exact pushed app commit build succeeds, resulting deploy commit pulled, Argo revision/image agree and public URL exercises the new journey. Existing healthy starter is insufficient proof.
- Dependencies: M8-ready local app; quota headroom; private cluster credentials configured out of git.
- Estimated effort: 60–120+ minutes; do not attempt inside recording reserve.

## Scope triage

Cut optional deployment, Ollama setup, visual polish and additional organizer features first. Do not replace real PostgreSQL/Elasticsearch or silently fake AI/Dash0 to claim completion. If time is insufficient, retain working registration and honestly identify uncompleted FRs in submission notes. No automatic infrastructure expansion or hosted investigation is authorized by this plan.
