# Changelog

## 2026-11-09

- Added the local organizer and attendee registration UI with private token entry, locally generated registration QR codes, attendee counts, retry-safe submissions, and proxy cookie coverage.
- Added an isolated Java 21 backend foundation, tested health/version endpoints, pinned local PostgreSQL/Elasticsearch services, and reproducible startup instructions.
- Recorded unavailable real-AI and Dash0 prerequisites without marking their milestones complete.
- Specified Conference Wingman's MVP requirements, architecture, API, data model, implementation milestones and recorded demo plan while preserving the starter integrations.
- Kept registration idempotency on the registration entity to simplify persistence and define safe concurrent retry behavior.
- Simplified the MVP model by keeping processing and private session fields on registration, with a separate table only for its multiple match results.
