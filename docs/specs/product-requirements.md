# Conference Wingman product requirements

Status: proposed specification; no application implementation authorized by this document.
Date: 2026-11-09. Submission deadline: 17:30 America/Denver, including a working app and a three-minute recorded video.

## Purpose and users

Help introverted conference attendees discover who to meet, why a connection matters, and what to say after event registration. Organizers create a conference and share its registration link. Wingman complements registration platforms; it does not integrate with Luma in this MVP.

## Repository findings and assumptions

- Inspected checkout: `/Users/manoj/Projects/wingman-cn-hackathon`, origin `git@github.com:manoj-r/wingman-cn-hackathon.git`, HEAD `1b19cde`. The requested `~/projects/cn-hackathon-wingman` differs; use the existing provided checkout rather than creating another repository.
- `app/server.js` is a dependency-free Node HTTP conference schedule service. `app/package.json` only defines `npm start`. There are no Next.js, Java, database, AI, Elasticsearch, Dash0, or Gitar configuration files in the inspected starter.
- Docker uses Node 22, non-root execution, port 8080 and `APP_VERSION`. Keep `/healthz`, `/readyz`, `/api/sessions` and version reporting available in any future replacement.
- GitHub Actions builds a public GHCR image and commits its tag into Kustomize. Argo CD tracks `main`; namespace is `team-wingman`, public URL is `https://wingman.cnh.radarhq.io`. Markdown-only changes do not trigger the build. Live cluster state was not inspected for this documentation task.
- Preserve `app/`, `Dockerfile`, `.github/workflows/deploy.yaml`, `deploy/`, `argocd/`, scripts, examples and agent instructions during the local MVP work. Existing untracked `.idea/` is outside scope.
- Local demonstration is acceptable. Docker and sufficient memory for Elasticsearch, Java 21, Node 22 and a usable AI provider are prerequisites to verify first. Credentials and actual Dash0 access are not assumed present.
- Demo attendees are fictional, with synthetic `example.test` email addresses. Real email delivery and real attendee imports are excluded.

## Journeys and functional acceptance

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| FR-001 | Organizer creates a conference with name, description, location and dates. | Valid request persists one conference; invalid dates return field errors. |
| FR-002 | Organizer receives a public registration URL and QR code. | QR decodes to the exact configured public registration URL, without organizer token. Management view shows registered attendee count. |
| FR-003 | Attendee supplies name, email, role, company, bio, interests and goals. | Responsive form validates fields, saves and opens their recommendations view. |
| FR-004 | Registration persists in PostgreSQL. | Attendee and registration survive backend restart; retries cannot duplicate registration. |
| FR-005 | Profile becomes an embedding. | Actual configured provider produces a finite, nonzero vector of configured dimension. |
| FR-006 | Profile and vector are indexed in Elasticsearch. | Document is scoped to its registration/conference and becomes searchable. |
| FR-007 | Elasticsearch kNN returns three relevant other attendees in this conference. | With at least three eligible profiles, response has three unique matches, excludes self and other conferences; with fewer, returns only available matches. |
| FR-008 | LLM explains each recommendation. | Each card has a short grounded explanation referring only to supplied professional profiles. |
| FR-009 | LLM generates a personalized conversation starter. | Each card contains one approachable, specific opener. |
| FR-010 | Attendee requests a friendly introduction. | “Help Me Connect” generates an editable/copyable introduction to use in person; UI explicitly says nothing is sent. |
| FR-011 | OpenTelemetry captures registration, embedding, search and LLM operations. | A correlated trace contains the four operations and errors/durations, without profile text, emails or secrets. |
| FR-012 | Dash0 receives application telemetry. | A new demo trace can be found in Dash0 by service name and trace ID; console output alone is insufficient. |

## MVP boundaries and usability

No payments, ticketing, messaging, calendar scheduling, email notifications, production authentication, mobile app or complex organizer administration. No promise that a recommended attendee agrees to connect. No sensitive-trait inference, fabricated mutual relationships or scoring labeled as compatibility percentage.

Every form and network action has disabled duplicate-submit controls, loading text, accessible labels, keyboard focus, and useful errors. Match processing must not lose a successful registration. Show queued/processing/unavailable states and retry guidance rather than an indefinite spinner. Target mobile width 375px and desktop width 1440px without horizontal overflow.

Engineering targets: conference creation/registration persistence within two seconds locally excluding AI work; recommendation processing budget 45 seconds for the small demo fixture. These are test targets, not measured claims. AI failure must leave registration usable. Three real kNN matches and actual LLM output are the primary demo path; labeled fallback text does not count as completing FR-008/009/012.

## Security and completion gate

Organizer access uses a cryptographically random private token; its hash is stored, and raw value is returned once. Never include it in public URLs or QR codes. Attendee recommendations use a separate private session capability. Public endpoints expose conference details, not attendee email or a browsable attendee directory. Secrets stay in local ignored environment files or out-of-git credential stores.

MVP is complete only after all FRs are exercised with real dependencies, data survives restart, cross-conference/privacy checks pass, seeded startup is reproducible, and the three-minute video is recorded and reviewed. Missing credentials, mocked AI and unverified Dash0 must be reported as gaps, not passed acceptance criteria.

Related: [architecture](architecture.md), [API](api-contracts.md), [data](data-model.md), [milestones](implementation-plan.md), [demo](demo-plan.md).
