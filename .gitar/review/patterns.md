# Project patterns and invariants

## Component ownership

- `app/` is the existing Node starter and remains independently routable until a deliberate migration changes that contract.
- `backend/` owns domain validation, authorization, persistence, matching, provider calls, and private API behavior. The browser must not call AI or Elasticsearch directly.
- `web/` owns presentation and same-origin API proxy behavior. It may hold a newly issued organizer token in tab memory for the intended flow, but must not persist provider keys or attendee capabilities in durable browser storage.
- `docs/specs/` defines intended API, data, architecture, and milestone contracts. When implementation and spec differ, identify the difference rather than silently assuming either is authoritative.

## Persistence and matching

- PostgreSQL is authoritative for identity, registration, capability hashes, and processing state. Elasticsearch is a rebuildable search projection.
- A successful registration must survive an embedding, Elasticsearch, or LLM outage. Downstream processing is recoverable and must not undo the registration.
- Keep idempotency and uniqueness guarantees at the persistence boundary, including concurrent duplicate submissions. Retry work safely; Elasticsearch writes should be idempotent.
- Matching must exclude the attendee and filter by conference in the kNN query. Validate candidates against authoritative PostgreSQL data before returning them.
- Flyway migrations own schema evolution; JPA validates the schema rather than silently mutating it. Review upgrades against existing rows and interrupted rollout behavior.
- Never place attendee email or capability material in public DTOs, search projections, embeddings/prompts, or telemetry.

## Credentials and untrusted content

- Local secrets belong in the ignored root `.env`, using `.env.example` only for variable names and safe placeholders.
- Cluster credentials belong in Kubernetes Secrets outside version control, in the correct namespace and following `examples/agent-workflows/README.md` when applicable. CI credentials belong in GitHub Actions Secrets.
- Never commit secret values, kubeconfigs, tokens, provider keys, or credentials in Helm values, manifests, examples, logs, PR descriptions, or client bundles.
- Profile text, prompts, and provider output are untrusted data. Validate structured output and candidate IDs; do not let text grant authority or trigger arbitrary actions.

## GitOps `deploy/` and Helm `charts/`

- `argocd/application.yaml` currently selects `deploy/`; those Kustomize manifests are the active GitOps input for the starter workload.
- `deploy/service.yaml` must preserve Service `app`, port 80, in `team-wingman`, because the platform URL routes there. Preserve the existing HTTPRoute behavior and other cluster constraints documented in `AGENTS.md`.
- `charts/wingman/` is reusable Helm packaging. It does not become active merely because it exists; changing the Argo CD source to Helm is a distinct migration with its own review and rollout requirements.
- Keep cluster-owned application state declarative in Git when Argo CD owns it. Do not patch those live resources as a substitute for a repo change; self-heal can revert the patch.
- Check resource requests/limits, PVC/storage budget, and forbidden host/privileged access in any workload or chart change.

## Change boundaries

- Keep database migrations with backend schema changes and update focused tests/contracts in the same change.
- Keep UI behavior and API proxy concerns in `web/`; enforce security and domain invariants in `backend/` too.
- Keep local Compose dependencies separate from cluster manifests. Local readiness does not establish cluster deployment readiness.
- Do not claim new folders deploy through the starter image workflow unless the workflow and routing were deliberately changed and verified.
