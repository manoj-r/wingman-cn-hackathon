# Conference Wingman three-minute demonstration

Deadline: 2026-11-09 17:30 America/Denver. Target feature freeze 16:45; reviewed video by 17:10; submit by 17:20. This is the requested three-minute product recording, separate from the starter's optional 5:30 Radar presentation procedure.

## Preparation and repeatability

1. Verify Docker, PostgreSQL, Elasticsearch, backend, frontend, selected embedding/LLM provider and Dash0. Use the documented local startup from M8; set `PUBLIC_APP_URL` to the actual demo origin. Localhost is valid for the laptop recording; do not suggest a phone can open its QR URL.
2. Idempotently seed the fictional Cloud Native Connections conference with six attendees covering platform engineering, observability, AI, community and developer experience. Include an isolation-control conference with a semantically similar profile. Use only synthetic `example.test` emails; do not type organizer tokens or API keys on camera.
3. Wait for all fixture embeddings/indexing and verify three matches for the intended new profile. Create a second conference during the recording to show organizer creation, but register the new attendee into the preseeded demo conference so matches already exist. Narrate this transition explicitly; do not imply the empty newly created conference already has attendees.
4. Open organizer management off camera with its private token. Prepare public registration and Dash0 tabs, hide bookmarks/notifications/terminal environment. Use a fresh attendee email for each take to avoid duplicate-registration errors. Do not reset persistent demo data during the recording.
5. Run `node scripts/demo-smoke.mjs` (to be implemented in M8), validate restart persistence, cross-conference exclusion and no emails in match responses. Keep a timestamped sanitized trace ID and screenshot; do not manufacture a trace if Dash0 is unavailable.

## Recording storyboard

| Time | Action | Spoken point / evidence |
| --- | --- | --- |
| 0:00–0:20 | Show Wingman landing page. | “Registration gets you into a conference. Wingman helps you find who to meet, why, and what to say—especially when starting conversations feels difficult.” |
| 0:20–0:45 | Create a conference; show registration URL and QR, hide private token section. | Organizer can create/share without a full account. QR and public link contain no management credential. |
| 0:45–1:20 | Switch explicitly to the fictional preseeded conference registration. Submit a new platform engineer interested in OpenTelemetry and developer experience. | “This conference already has our fictional demo attendees.” Show clear loading state after successful persistence. |
| 1:20–1:55 | Show three recommended connection cards. Read one explanation and opener. | Explain actual semantic search restricted to this conference; show the concrete shared interest, not a compatibility percentage. |
| 1:55–2:15 | Click Help Me Connect; edit/copy introduction. | “This gives me something friendly to say. It does not send a message.” |
| 2:15–2:35 | Show pre-opened private organizer count view and refresh. | Registered attendee count increased, backed by PostgreSQL; attendee emails remain private. |
| 2:35–2:55 | Show Dash0 trace for this run with registration, embedding, search and LLM spans. | Point out durations and real provider operations. Mention retained Radar/GitOps starter; do not claim local Wingman is deployed publicly. |
| 2:55–3:00 | Return to connection cards. | “Meaningful connections, with a comfortable first step.” |

## Rehearsal and failure handling

- Record a timed rehearsal first. The full new-registration processing must fit its segment; shorten narration or use a clearly identified earlier completed registration if external provider latency exceeds it. Never hide a failed dependency as live success.
- If QR is shown locally, demonstrate its decoded URL on the same computer. For a public demo, verify the actual origin and route before changing configured URLs.
- Provider failure: show truthful retry/degraded state and retain successful registration; a labeled template is an optional recovery path, not proof of LLM acceptance. A previously captured successful run can illustrate behavior if clearly described as earlier footage.
- Dash0 outage/missing credentials: report FR-012 incomplete. Do not present console spans as Dash0 ingestion. Radar observations, if included, must be actual evidence from the team's cluster; no induced incidents.
- Preserve at least 30 minutes for recording, reviewing audio/text readability and uploading. Do not attempt new infrastructure during that reserve.

## Submission checklist

- [ ] Local startup instructions work from documented prerequisites with pinned dependencies.
- [ ] Seed is fictional/idempotent; actual PostgreSQL, embedding, Elasticsearch kNN and LLM used.
- [ ] FR-001–FR-012 checked; any failures named explicitly.
- [ ] Organizer token absent from public link, QR, browser address bar and recorded screen.
- [ ] Attendee email, provider credentials and OTLP headers absent from cards, logs and video.
- [ ] Working app location and startup instructions supplied; public starter URL not misrepresented as Wingman.
- [ ] Video is at most three minutes, has audible narration and has been played back.
- [ ] Repository changes and video submitted before 17:30 Mountain; destination/link verified manually.
