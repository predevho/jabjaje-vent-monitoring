# jabjaje-vent-monitoring

## Coding standards

These rules apply to every code change. `/code-review` checks against them.

### Package layout: feature → layer (ADR-0008)

Code lives under `server/src/main/java/vent/`. Each feature (named after a GLOSSARY.md term) gets its own package, and inside it one sub-package per layer:

```
vent/<feature>/
├── controller/      REST endpoints and screens. No business logic, no @Transactional.
├── service/         Use cases. Owns @Transactional boundaries.
├── repository/      Spring Data JPA repositories (one per aggregate root). Native SQL only where PostgreSQL features are needed.
├── domain/          JPA entities (one file per table) and the enums they use.
└── dto/
    ├── request/     Incoming request bodies and query parameters.
    └── response/    Outgoing response bodies (one record per file). Never expose entities directly.
```

Create a layer folder only when the feature has a class for it (an empty `dto/request/` is not committed).

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
