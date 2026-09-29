# Implementation Roadmap

Each phase ends with the same gate: **build → test → review → fix → document → static analysis**. The next phase starts only when the gate is green. "Done" means verified by an executed test or run, never by assertion.

## Changes to the requested phase order

The requested order (security in phase 13, testing in 15, Docker in 16) would produce ten phases of untested, unauthenticated code. This order was changed:

1. **Testing is continuous.** Every phase ships its unit and Testcontainers tests. Phase 15 adds E2E, failure-injection and performance only.
2. **Security baseline arrives with the first API** (JWT + RBAC in Phase 3). Phase 13 hardens it: token exchange, mTLS, scans and the threat-model tests.
3. **Local infra compose arrives in Phase 1.** Service Dockerfiles are hardened in Phase 16.
4. **A deterministic end-to-end slice (no AI) is completed before any AI work** (end of Phase 6). This proves principle P2 (the platform works without an LLM) and gives AI features real data to use.
5. Guardrails come before agents, because agents must never exist unguarded.

## Phases

| # | Phase | Scope | Exit criteria (verified) |
|---|---|---|---|
| 0 | **Architecture + ADRs** | This document set | Reviewed and accepted; open questions answered |
| 1 | Repository + build | Maven wrapper, parent POM with pinned versions (Boot 4.0.8, Spring Cloud 2025.1.3, Testcontainers 2.x), `platform/common-web` (correlation IDs, RFC 9457 errors), Spotless/SpotBugs/JaCoCo/enforcer/ArchUnit; Python workspace (uv, ruff, mypy strict, pytest) with the ai-orchestrator service shell; CI workflow; `docker-compose` `core` profile (Postgres with schema-per-service roles, Kafka KRaft, Schema Registry, Redis, Keycloak realm with roles/clients, LocalStack with S3/KMS/Secrets init); Makefile; `.env.example` with placeholder values | `./mvnw verify` and `uv run pytest` green; `make up-core` healthy |
| 2 | Core domain + PostgreSQL | Domain models + Flyway migrations for customer, segment, offer, campaign; campaign state machine; eligibility engine; optimistic locking | Domain unit tests; Testcontainers repo tests; migrations apply clean from zero; ArchUnit rules pass |
| 3 | REST APIs + security baseline | Controllers, DTOs, validation, ProblemDetail, pagination, ETag, idempotency (DB-backed), OpenAPI, JWT resource servers, RBAC matrix, tenant scoping, api-gateway routing + correlation | MockMvc tests incl. generated role-matrix and cross-tenant tests; OpenAPI published |
| 4 | Kafka + events | Avro schemas + registry compat check, envelope, outbox + leader publisher, consumer idempotency, blocking/non-blocking retry, DLQ + replay CLI, trace propagation | Integration tests: outbox survives Kafka outage; duplicate event is no-op; poison → DLQ |
| 5 | Streaming + data platform | event-ingestion-service, data-generator, stream-processor (dedup, daily buckets, features, late events, attribution, metrics two-stage aggregation), Kafka Connect → S3 (LocalStack locally), curation job with DQ checks + lineage manifest | TopologyTestDriver tests (event time, late, dup, windows); Testcontainers pipeline test; DQ report produced |
| 6 | Deterministic E2E slice | segmentation incremental + backfill, offer eligibility consumer, personalization (template mode), notification (simulated), analytics-service, audit-service with hash chain | **E2E test: generated events → features → segment → eligibility → campaign publish → notification → engagement → metrics** — green with no LLM configured |
| 7 | Redis | Caches with event-driven eviction, gateway rate limiting, idempotency fast path, frequency caps | Tests incl. Redis-down behavior (falls back to DB) |
| 8 | AI gateway | Provider adapters (Anthropic, OpenAI, local, stub), router, resilience, prompt registry, structured output + repair, egress policy + PII detector, cost/tokens, `ai.events` | Unit tests with stub; WireMock provider-failure tests (timeout, 429, 5xx, invalid JSON → fallback) |
| 9 | RAG | Synthetic knowledge corpus, ingestion pipeline, OpenSearch hybrid + ACL filter, ONNX embed + rerank, threshold, citations, Postgres FTS fallback, versioning | Retrieval metrics on `rag_qa` above agreed floors; ACL leak tests; OpenSearch-down test |
| 10 | Guardrails | Guardrail engine module in ai-gateway + rule packs + classifier integration | `adversarial` deterministic subset passes |
| 11 | MCP servers | marketing + customer MCP, OAuth resource server, allow-lists, audit, rate limits, resources, prompts | MCP client integration tests; unauthorized tool → denied + audited |
| 12 | Agents | Python/LangGraph orchestrator graphs (copilot, campaign generation, analytics), token exchange, parallel tools, insight engine, memory tiers, compliance agent + A2A card, SSE | Tool-selection and graph tests with stub model; degraded-mode tests |
| 13 | AI evaluation | evaluation-service (Python), datasets, metrics, judge + agreement check, baselines, CI `ai-eval` job, online sampling | CI gate demonstrably fails on a deliberately degraded prompt |
| 14 | Security hardening | Token exchange audit, Linkerd mTLS config, CodeQL/Trivy/gitleaks/dependency-check/checkov in CI, threat-model tests | All scans green without suppressions lacking expiry |
| 15 | Observability | OTel collector, Prometheus, Tempo, Loki, Grafana dashboards (app, Kafka, DB, AI, business) as code, alert rules | One trace spans gateway → Kafka → personalization → ai-gateway; dashboards render with demo data |
| 16 | Frontend | marketing-console (12 screens), OIDC login | Playwright smoke tests |
| 17 | E2E, failure & performance testing | Full E2E suite, failure-injection scenarios (LLM down, Kafka down, DB down, OpenSearch down, tool timeout, dup event, invalid JSON, unauthorized tool, injection), Gatling + Kafka load | Results documented in `performance.md` with hardware context |
| 18 | Containers + Kubernetes | Hardened Dockerfiles, Helm library chart + per-service values, HPA, PDB, probes, topology spread, kind deploy in CI | `helm lint`, kubeconform, kind smoke test green |
| 19 | Terraform / AWS | Modules (VPC, EKS, RDS, ElastiCache, MSK, S3, OpenSearch, KMS, Secrets Manager, WAF, IAM/IRSA, CloudWatch), env roots, cost guardrails | `terraform validate`, tflint, checkov green; `plan` for dev documented; **no apply without explicit approval** |
| 20 | CI/CD completion + demo + ops docs | Full pipeline, release workflow, demo script (18 steps), runbook, DR doc, deployment guides | Demo runs end-to-end from a clean checkout via `make demo` |

## Effort and environment notes

- This is a multi-session build. Each phase is delivered and verified separately.
- The workstation has 8 GB RAM, so the full stack cannot run at once locally (see [architecture.md §10.1](architecture.md)). The full E2E run is done in CI.
- Tooling to install before the relevant phases: Terraform + tflint + tflocal (19), Helm + kind + kubeconform (18). Java 25, Maven, Docker, Node and uv are already present.
- A free `LOCALSTACK_AUTH_TOKEN` is needed in `.env` ([ADR-023](decisions/ADR-023-localstack-local-aws.md)).
