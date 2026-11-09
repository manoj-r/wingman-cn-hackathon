# Continuation handoff

Last checked: 2026-11-09. Treat GitHub and the current repository state as the source of truth; PR status can change after this note was written.

## Repository and current work

- Repository: `manoj-r/wingman-cn-hackathon` (`/Users/manoj/Projects/wingman-cn-hackathon`).
- This review-guidance change is intended as a standalone branch/PR targeting `main`; it must not include application or deployment changes and must not be merged by the agent.
- The checked-out workspace at handoff creation was `chore/feature-workspaces`, based on `main`, with `.idea/` untracked. Leave `.idea/` and unrelated changes alone. The temporary isolated feature worktrees live under `.worktrees/`.
- A local worktree may contain more recent files/branches than the `main`-based PRs. Inspect `git status`, branches, and PRs before continuing; do not assume the current checkout is a clean `main`.

## Architecture and ownership

- The product is an additive Next.js/TypeScript/Tailwind frontend in `web/` and Java 21 Spring Boot backend in `backend/`, with PostgreSQL as source of truth, Elasticsearch as a rebuildable vector/search projection, real embedding/LLM providers, and OpenTelemetry to Dash0.
- `app/` is the original Node starter and remains independently routed until a deliberate packaging/routing milestone. New `web/` or `backend/` files do not automatically ship in the starter image.
- `argocd/application.yaml` currently points Argo CD at `deploy/`, which contains the active raw/Kustomize starter manifests. `charts/wingman/` is optional reusable Helm packaging and does not switch Argo CD to Helm.
- Preserve Service `app` on port 80 in namespace `team-wingman`; public URL is `https://wingman.cnh.radarhq.io`. The in-cluster HTTPRoute has no Gateway and should remain as documented. See repository `AGENTS.md` for exact cluster rules.
- Detailed contracts and milestones: `docs/specs/architecture.md`, `api-contracts.md`, `data-model.md`, `product-requirements.md`, and `implementation-plan.md`.

## Completed and open PRs (last observed)

| PR | State then | Branch / commit | Work |
| --- | --- | --- | --- |
| [#1](https://github.com/manoj-r/wingman-cn-hackathon/pull/1) | Merged | `feature/backend-foundation` / `622085a0fe93f913035593782cff3b552dd5d812` | M1 backend foundation |
| [#2](https://github.com/manoj-r/wingman-cn-hackathon/pull/2) | Open | `feature/helm-deployment` / `b013a50f6726521a54402e514445ac411a77c45f` | Optional Helm packaging and backend container build |
| [#3](https://github.com/manoj-r/wingman-cn-hackathon/pull/3) | Open | `feature/organizer-registration-ui` / `a7fc77e40217785faf8ce73905f38888c113830d` | Organizer and attendee registration UI; `/connections` is still a placeholder |
| [#4](https://github.com/manoj-r/wingman-cn-hackathon/pull/4) | Open | `feature/conference-registration` / `c066c3ea01bd6611ccaa04d096484aa46e57cc9f` | M2 conference and registration persistence/security |
| [#5](https://github.com/manoj-r/wingman-cn-hackathon/pull/5) | Open, dependent on #4 | `feature/semantic-matching` / `3879cdbae9f6c6db6b2b22a93feeed5c0f908045` | M3 semantic matching, targets `feature/conference-registration` |

M2's focused Gradle suite and reported PostgreSQL restart/concurrency checks passed. M3 focused tests and `git diff --check` passed; its PostgreSQL integration run could not start because localhost:5432 was unavailable, and real embedding smoke test was blocked by provider HTTP 429/unavailable credentials. Do not describe those external checks as passed. The UI PR's build/typecheck and four browser tests passed using fixtures; real backend integration remains unverified. Helm lint/render/package validation passed. Recheck each PR before making decisions.

## Next implementation phases

Follow `docs/specs/implementation-plan.md` and keep PRs small and dependency-aware:

1. Review/merge the standalone review-conventions PR first, then review and merge the independent open PRs as appropriate; M3 depends on M2 and should target its branch until the dependency is integrated.
2. M4: grounded, validated LLM explanations/openers and authorized introduction generation; depends on M3 and usable provider access.
3. M6: recommendation cards and connection interaction; depends on M4 and the registration UI (M5).
4. M7: end-to-end telemetry, then M8: idempotent seed, rehearsal, privacy/isolation checks, and demo recording/submission.
5. Any cluster migration/deployment is separate and optional. Do not imply the local app is live at the public URL until packaging/routing is intentionally completed and the repository ship procedure verifies it.

Plan order is M0 → M1 → M2 → M3 → M4 → M6 → M8; M5 can follow M2; M7 must be complete before M8 acceptance. Confirm real provider availability before claiming AI acceptance.

## Secret handling

- Local development secrets: ignored root `.env`, based on `.env.example`; never commit actual values.
- Cluster credentials: Kubernetes Secret in `team-wingman`, managed according to the agent-workflows example and kept outside Git when the workflow requires it.
- CI credentials: GitHub Actions Secrets.
- Never put keys or tokens in Helm values, deploy manifests, browser code/config, logs, telemetry, PR descriptions, saved handoffs, or tool output. Never write kubeconfig/token material into the public repository.
- Organizer token is generated with high entropy; only its hash is persisted, and the raw value is shown once for private copying. Attendee capability is an HttpOnly cookie with only its hash stored. No email in public match responses, search projection, embedding/prompt, or telemetry.

## Cluster and GitOps constraints

- Use Radar Cloud MCP for cluster observation/change. Call `list_clusters` once per session, select the cluster whose exact name is `wingman`, and pass its ID as `cluster` to every subsequent call. Use namespace `team-wingman`; never touch `cnh` or another team cluster. Read `AGENTS.md` before cluster work.
- GitOps deploys from `main`; Argo CD owns app manifests. Change Git and follow the `ship` procedure for a requested deployment. Do not patch Argo-owned live resources. Confirm synced revision, new workload image/health, and public URL before claiming deployment.
- Respect the shared budget: 2 CPU / 4 GiB requests, 4 CPU / 8 GiB limits, 4 PVCs / 20 GiB; no privileged, hostPath, hostNetwork, or hostPID pods. Do not remove/reconfigure preinstalled platform components.
- Keep the `app` Service contract. Helm packaging in `charts/` is not active unless Argo CD source is deliberately changed.

## Agent execution preference

The user explicitly requires implementation work to be performed in subagents and all future subagents to use **model `gpt-6-luna` with `reasoning_effort: low`**. Keep subagent tasks scoped, use isolated worktrees for independent changes, require focused verification and a PR, and do not merge unless asked. If subagent tooling is unavailable in a session, state that limitation and do not misrepresent direct work as delegated.
