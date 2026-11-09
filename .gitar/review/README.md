# Repository review guide

Use these notes when reviewing a change to Conference Wingman. They describe a practical review order and the project invariants that matter most. They are guidance for human and agent reviewers, not a substitute for the change's own tests or the repository's `AGENTS.md` instructions.

## Review order

1. Understand the change's user-visible behavior and the files it owns. Compare it with `docs/specs/` and the current implementation plan.
2. Check correctness and data flow: validation, authorization, persistence, retries, concurrency, and failure behavior.
3. Check trust boundaries, privacy, secrets, and data-loss risks.
4. Check compatibility with the existing starter, GitOps contract, API shapes, and migrations.
5. Review tests and run the smallest relevant verification available. Report what was and was not exercised.
6. Summarize actionable findings first, ordered by severity, with file and line references. If there are no findings, say so and mention material verification gaps.

## Severity

- **Blocker** — likely data loss, credential exposure, unauthorized access, or a broken release/deployment invariant; must be fixed before merge.
- **High** — major user-facing behavior or correctness failure, security weakness, or likely production outage.
- **Medium** — bounded defect or important maintainability/coverage gap that should normally be fixed in this change.
- **Low** — small improvement or polish; non-blocking unless it obscures correctness.

Use the lowest severity that still communicates the consequence. Explain impact and a reproducible condition; do not label preferences as defects.

## Required checks

Review authorization at every boundary: organizer bearer token, attendee capability cookie, API proxy, and conference ownership. Treat profile text and provider output as untrusted. Ensure emails, credentials, capability values, prompts, and private profile content do not leak into public responses, browser storage, logs, telemetry, search documents, or errors.

Protect persisted registrations from downstream AI or search failures. Check idempotency, transaction boundaries, concurrent requests, retry/lease recovery, and migration compatibility. Do not accept destructive schema/data changes without a safe migration and a clear rollback or recovery story.

For deployment changes, confirm GitOps-owned resources remain represented in the repo, retain the required `app` Service contract, and fit cluster restrictions and quota. Do not mistake the reusable Helm chart for the active Argo CD source: inspect `argocd/application.yaml` and `deploy/`.

## Verification and using these files

Read [conventions.md](conventions.md) for review habits, [patterns.md](patterns.md) for project-specific ownership and invariants, and [template.md](template.md) to structure a review. Use commands appropriate to the changed component; prefer focused tests, build/type checks, manifest or chart validation, and `git diff --check`. Do not claim a check passed unless it was run. If external services, credentials, or test infrastructure are unavailable, state the limitation precisely.

These files do not authorize merging, deploying, changing cluster state, or broadening a task. Follow repository instructions and the user's requested scope.
