# API Design

Status: Phase 0 conventions and endpoint catalog. The generated OpenAPI specs (springdoc) become the source of truth from Phase 4, published per service at `/v3/api-docs` and aggregated by the gateway.

## 1. Conventions

| Topic | Rule |
|---|---|
| Versioning | URI major version `/api/v1/...`. Additive changes only within a version. Breaking change → `/api/v2` served in parallel, `Deprecation` + `Sunset` headers on v1 |
| Auth | `Authorization: Bearer <JWT>` on every endpoint except health. OpenAPI declares the `oauth2` security scheme and required permission per operation |
| Correlation | `X-Correlation-Id` accepted or generated at the gateway, echoed in the response, propagated everywhere |
| Idempotency | `Idempotency-Key` (UUID) **required** on `POST` that creates resources or triggers transitions (`/campaigns`, `/campaigns/{id}/publish`, `/offers`, `/segments`, `/events`, `/ai/campaign/generate`). Same key + same body → stored response replayed. Same key + different body → `422` |
| Concurrency | `ETag` (= aggregate version) on GET; `If-Match` required on `PUT`/`PATCH`; mismatch → `412` |
| Pagination | Cursor-based: `?limit=20&cursor=<opaque>`; response `{items, nextCursor}`. `limit ≤ 100`. Offset pagination only for small admin lists |
| Filtering / sorting | Explicit allow-listed params (`status=ACTIVE&channel=EMAIL`), `sort=createdAt,desc`. Unknown params → `400` |
| Errors | RFC 9457 (successor of RFC 7807) `application/problem+json` |
| Time | ISO-8601 UTC. Money as `{amount: "12.50", currency: "USD"}` (string decimal) |
| Async | Long operations return `202 Accepted` + `Location` of a status resource |

### Error format

```json
{
  "type": "https://errors.vasmarketing.example/campaign/invalid-state-transition",
  "title": "Invalid campaign state transition",
  "status": 409,
  "detail": "Campaign 0191… is DRAFT; publish requires APPROVED.",
  "instance": "/api/v1/campaigns/0191…/publish",
  "correlationId": "c-7f3…",
  "errorCode": "CAMPAIGN_INVALID_TRANSITION",
  "violations": [ { "field": "startAt", "message": "must be in the future" } ]
}
```

Status mapping: `400` validation, `401` unauthenticated, `403` forbidden, `404` not found (also used for other tenants' resources, to avoid ID probing), `409` state conflict / duplicate, `412` stale ETag, `422` idempotency key reuse with different body, `429` rate limited (+`Retry-After`), `503` dependency unavailable (+`Retry-After`), `504` upstream timeout.

### As implemented (Phase 3)

| Concern | Implementation |
|---|---|
| Errors | `platform/common-web` `ProblemDetailsExceptionHandler`. Each service declares an `ErrorStatusMapping` from its error codes to HTTP statuses. An unmapped `DomainRuleViolation` returns 422. `ResourceNotFound` returns 404, a stale `If-Match` 412, a missing `If-Match` 428, and an optimistic-lock race 409 `CONCURRENT_MODIFICATION` |
| 401 / 403 | `platform/common-security`: problem+json from both the filter chain and method security (`@PreAuthorize` on each controller operation) |
| Idempotency | `platform/common-idempotency`. Endpoints annotated `@Idempotent` require `Idempotency-Key`. Keys are scoped to the principal and bound to method, path, query and body (SHA-256). Completed responses (status < 500) are replayed with `Idempotent-Replayed: true` for 24 h. The same key with a different body returns 422 `IDEMPOTENCY_KEY_REUSED`. A request still in flight returns 409 with `Retry-After`. A 5xx releases the key. Records live in each service's schema under a separate Flyway history (`platform_schema_history`) |
| ETags | Strong ETag = aggregate version (`"3"`). PUT requires `If-Match`. State transitions accept it optionally. Weak validators are rejected |
| Pagination | Keyset over UUIDv7 ids, newest first. `limit` 1–100, opaque `cursor` (`INVALID_CURSOR` / `INVALID_PAGE_SIZE` are 400). Listings use constructor projections, so there is no N+1 |
| OpenAPI | springdoc at `/v3/api-docs` and `/swagger-ui.html` on every service, with the bearer scheme documented automatically |
| Gateway | `services/api-gateway` (Spring Cloud Gateway Server WebMVC): path routes, JWT validation and correlation IDs at the edge. Services validate the token again |

Deferred to later phases, where their data sources exist: `/offers/{id}/eligibility-check` and `/segments/{id}/members` (Phase 6), `/campaigns/{id}/analytics` (Phase 6), AI endpoints (Phases 8–12).

## 2. Endpoint catalog

| Method & path | Service | Permission | Notes |
|---|---|---|---|
| `POST /api/v1/campaigns` | campaign | campaign:draft | Idempotency-Key; 201 + Location |
| `GET /api/v1/campaigns` | campaign | campaign:read | filters: status, channel, segmentId, from/to |
| `GET /api/v1/campaigns/{id}` | campaign | campaign:read | ETag |
| `PUT /api/v1/campaigns/{id}` | campaign | campaign:draft | If-Match; DRAFT only |
| `POST /api/v1/campaigns/{id}/variants` | campaign | campaign:draft | |
| `POST /api/v1/campaigns/{id}/submit` | campaign | campaign:submit | |
| `POST /api/v1/campaigns/{id}/approvals` | campaign | campaign:approve | four-eyes |
| `POST /api/v1/campaigns/{id}/publish` | campaign | campaign:publish | Idempotency-Key |
| `POST /api/v1/campaigns/{id}/pause` · `/resume` | campaign | campaign:publish | |
| `GET /api/v1/campaigns/{id}/analytics` | analytics | analytics:read | `?from&to&granularity=HOUR|DAY&groupBy=segment,channel` |
| `POST /api/v1/analytics/query` | analytics | analytics:read | typed metric query (allow-listed metrics × dimensions) |
| `POST /api/v1/analytics/compare` | analytics | analytics:read | campaigns or periods; includes significance |
| `POST /api/v1/offers` · `GET /api/v1/offers/{id}` · `GET /api/v1/offers` | offer | offer:write / offer:read | |
| `POST /api/v1/offers/{id}/eligibility-check` | offer | offer:read (service) | deterministic decision + reason codes |
| `GET /api/v1/customers/{id}` | customer | customer:profile:read | masked |
| `GET /api/v1/customers/{id}/profile` | customer | customer:profile:read | features + preferences, masked |
| `POST /api/v1/segments` · `GET /api/v1/segments/{id}` · `GET /api/v1/segments/{id}/members` | segmentation | segment:write / segment:read | members paged, IDs only |
| `POST /api/v1/segments/{id}/activate` | segmentation | segment:write | 202, async backfill |
| `POST /api/v1/events` | event-ingestion | client credentials + HMAC | batch ≤ 500, 202 |
| `POST /api/v1/ai/chat` | ai-orchestrator | ai:chat | SSE when `Accept: text/event-stream` |
| `POST /api/v1/ai/campaign/generate` | ai-orchestrator | ai:generate + campaign:draft | Idempotency-Key; returns draft id |
| `POST /api/v1/ai/analytics/query` | ai-orchestrator | ai:chat + analytics:read | |
| `GET/DELETE /api/v1/ai/memory` | ai-orchestrator | ai:chat (own data) | |
| `POST /api/v1/rag/search` | rag | knowledge:read | ACL-filtered; returns passages + citations |
| `POST /api/v1/rag/documents` | rag | knowledge:write | multipart, 202 |
| `GET /api/v1/ai/telemetry/*` | ai-gateway | ai:telemetry:read | cost/latency aggregates |
| `POST /api/v1/evaluations/runs` · `GET …/runs/{id}` | evaluation | ai:admin | |
| `GET /api/v1/audit/events` | audit | audit:read | filters: actor, resource, action, from/to |

### AI chat response contract

```json
{
  "conversationId": "…", "runId": "…", "intent": "ANALYTICS_QA",
  "answer": "…",
  "facts": [ { "label": "CTR (frequent travelers)", "value": 0.052, "unit": "ratio", "source": "tool:getSegmentBreakdown" } ],
  "interpretations": [ "Higher CTR likely reflects …" ],
  "caveats": [ "Sample for segment X is small (n=142); difference not significant (p=0.21)." ],
  "sources": [ { "documentId": "…", "version": 3, "title": "Travel campaign guidelines", "chunk": 4 } ],
  "toolsUsed": [ { "tool": "getCampaignMetrics", "latencyMs": 84, "status": "OK" } ],
  "quality": { "grounded": true, "guardrail": "PASS", "degraded": false },
  "reasoningSummary": "Compared segment-level CTR/CVR against the campaign average and the prior travel campaign.",
  "model": { "id": "…", "promptVersion": "marketing_analytics/v1" },
  "timestamp": "2026-09-29T10:15:00Z"
}
```
