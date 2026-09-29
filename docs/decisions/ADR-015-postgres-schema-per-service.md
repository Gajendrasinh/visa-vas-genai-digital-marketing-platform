# ADR-015: PostgreSQL, schema-per-service

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Services must own their data, but running one RDS instance per service is costly for a reference platform.

## 2. Options
1. Shared schema
2. Schema per service on shared cluster
3. Database instance per service

## 3. Decision
One RDS PostgreSQL cluster per environment, one schema + DB role per service, no cross-schema grants, Flyway per service. High-volume services can move to their own instance without code changes (connection config only).

## 4. Trade-offs
Shared cluster = shared noisy-neighbor and failure domain. Accepted for cost; mitigated with connection limits per role and separate instances for hot services if needed.

## 5. Scaling implications
Read replicas for analytics reads; native partitioning for time-series tables.

## 6. Failure modes
RDS Multi-AZ failover (~60–120 s); services use circuit breakers and retries with jitter.

## 7. Security implications
Least-privilege roles; RLS on PII tables; encryption with KMS; IAM DB auth optional.

## 8. Operational implications
Per-service migrations run as an init step with Flyway locking.

## 9. Cost implications
One cluster instead of ~12.
