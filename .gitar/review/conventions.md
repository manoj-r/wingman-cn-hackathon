# Code review conventions

## Findings

- Lead with findings, from highest severity to lowest. For each, identify the exact file/line, explain the failure mode and impact, and give a concise correction direction.
- Findings should be actionable and tied to behavior. Avoid speculative concerns, duplicate comments, style-only opinions, and requests unrelated to the change.
- Distinguish a proven defect from a question or a verification gap. Ask a question when context is missing; do not state an assumption as fact.
- Review the complete diff and relevant callers, data model, tests, and deployment configuration. A locally plausible change can still break a contract elsewhere.
- A clean review should state that no actionable findings were found and name any important checks that could not be performed.

## Correctness and design

- Trace behavior from request/UI through the owning backend or frontend code to persistence and external systems, then back through responses and failure states.
- Put invariants in the layer that owns them. Reuse an existing type, helper, framework facility, or dependency when it already expresses the rule.
- Prefer a small complete implementation over speculative extension points, duplicate validation, or abstractions with only one caller.
- Check null/empty/boundary inputs, timeouts, retries, concurrency, stale data, and partial failures where they apply.
- Preserve established API response shapes and compatibility unless the change explicitly updates the contract and its consumers.

## Security, privacy, and data

- Treat browser input, imported/profile text, provider responses, and data read from search indexes as untrusted.
- Verify authentication and authorization at the server boundary, including ownership and resource scope. UI hiding is not authorization.
- Keep provider and cluster credentials server-side. Never commit secrets or place them in public configuration, URLs, browser bundles, logs, telemetry, test snapshots, or review text.
- Minimize personal data. In particular, attendee email and capability material must not enter public match results, embeddings, prompts, search projections, or telemetry.
- For persistent changes, examine transaction atomicity, uniqueness/idempotency, concurrent requests, migration rollout, and recovery from interruption. Avoid silent destructive defaults.

## Verification

- Match verification to the change: focused unit/integration tests for behavior; frontend type/build/browser checks for UI; migration checks for schema; Kustomize/Helm rendering and linting for packaging.
- Prefer a focused check that exercises the changed invariant, then run broader checks when proportionate and available.
- Verify the original starter contracts when changes affect shared routing or build/deploy behavior.
- Report exact checks run and their outcome. Tests that use fixtures do not prove real database/provider integration; static rendering does not prove a live rollout.
- Do not infer deployment from a successful build or a healthy old workload. Deployment claims require the repo's ship procedure and evidence of the new revision/image and URL.
