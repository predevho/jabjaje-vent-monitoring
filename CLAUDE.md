# jabjaje-vent-monitoring

## Coding standards

These rules apply to every code change. `/code-review` checks against them.

### Package layout: feature → layer (ADR-0008)

Code lives under `server/src/main/java/vent/`. Each feature (named after a GLOSSARY.md term) gets its own package, and inside it one sub-package per layer:

```
vent/<feature>/
├── controller/      HTTP (REST) endpoints. No business logic, no @Transactional.
├── listener/        Message entry points (MQTT subscriptions). Same rules as controller.
├── service/         Use cases. Owns @Transactional boundaries.
├── repository/      Spring Data JPA repositories (one per aggregate root). Native SQL only where PostgreSQL features are needed.
├── domain/          JPA entities, one file per table.
│   └── type/        Enums used by the entities (states, kinds, sources).
└── dto/
    ├── request/     Incoming request bodies and query parameters.
    └── response/    Outgoing response bodies (one record per file). Never expose entities directly.
```

Create a layer folder only when the feature has a class for it (an empty `dto/request/` is not committed).

Inbound messages from devices (MQTT) are handled like HTTP requests but kept apart from them: the MQTT listener sits in the feature's `listener/` and the message shape in `dto/request/`. Infrastructure shared by several features (the MQTT client connection) lives in `vent/global/<topic>/`.

Settings records (`@ConfigurationProperties`) live next to the code that reads them: a feature's own settings in that feature's `service/` (e.g. `TelemetryQualityProperties`), shared infrastructure settings in `vent/global/<topic>/` (e.g. `MqttBrokerProperties`).

Name types after the feature they belong to (`TelemetryIngestResult`, not `IngestResult`) so a name stays unambiguous when another feature grows a similar type.

### Simulator (`simulator/`)

Plain Java 17, no Spring. Package `vent.simulator`. Same documentation-comment rule as the server.

### Web (`web/`, ADR-0010)

React + TypeScript + Vite. Screens are for verifying features, so keep them simple.

```
web/src/
├── pages/        One component per screen.
├── components/   Reusable pieces used by pages.
├── api/          Functions that call the server's `/api` endpoints.
└── types/        TypeScript types that mirror the server's response DTOs.
```

Every `.ts`/`.tsx` file starts with a JSDoc block stating what it is, `@author predevho (AI 보조: Claude)`, and `@since <date> (#<issue>)`. Use GLOSSARY.md terms for on-screen Korean text.

### Database migrations

Flyway migrations live in `server/src/main/resources/db/migration/`. Never edit a migration once it has been committed, not even its comments: Flyway checks each file's checksum and refuses to start when an applied file changed. Make every schema change as a new `V<n>__<what>.sql`.

Demo seed data never goes into `db/migration/`. It lives in `db/seed/` as an idempotent repeatable migration (`R__*.sql`, `ON CONFLICT DO NOTHING`) that only the `demo` profile loads (ADR-0011). Tests that need the seed use `@ActiveProfiles("demo")`.

### Documentation comments

Every class, record, enum, interface, test class, and SQL migration starts with a documentation comment that states what it is, who wrote it, and when:

```java
/**
 * 현장 목록 조회 API.
 *
 * <p>현장과 그 게이트웨이, 장비를 함께 돌려준다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
```

- First sentence: what the code is, in Korean, using GLOSSARY.md terms.
- `@author predevho (AI 보조: Claude)`.
- `@since <date it was first written> (#<issue>)`. Do not update it on later edits; git history records changes.
- SQL migrations use the same three facts in a `--` header.

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues (predevho/jabjaje-vent-monitoring) via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the five default triage labels (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` at the repo root and ADRs in `docs/adr/`. See `docs/agents/domain.md`.
