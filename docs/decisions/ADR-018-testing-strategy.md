# ADR-018: Testing strategy

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
A distributed, AI-heavy system needs confidence at unit, integration, contract, E2E, AI-quality and performance levels without flaky CI.

## 2. Options
1. Mostly E2E
2. Test pyramid with Testcontainers, contracts and separate AI evaluation

## 3. Decision
Unit (JUnit 5, Mockito, AssertJ) for domain logic; ArchUnit for architecture rules; Testcontainers for Postgres/Kafka/Redis/OpenSearch/Keycloak; Spring Cloud Contract for sync APIs between services; TopologyTestDriver for streams; E2E in CI on the full compose stack; AI evaluation as its own gated suite; Gatling for performance. Coverage floor 80 % lines on domain/application packages, enforced by JaCoCo and never lowered to pass.

## 4. Trade-offs
Longer CI; mitigated by change-based module selection and parallel jobs.

## 5. Scaling implications
n/a

## 6. Failure modes
Deterministic stub model for functional tests; live models only in protected eval jobs.

## 7. Security implications
Security tests (role matrix, cross-tenant) are generated, not hand-picked.

## 8. Operational implications
Flaky tests are quarantined with an issue and a deadline, never silently skipped.

## 9. Cost implications
CI minutes; live-model eval has a budget cap.
