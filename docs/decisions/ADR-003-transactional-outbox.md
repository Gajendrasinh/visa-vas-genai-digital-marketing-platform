# ADR-003: Transactional outbox for DB → Kafka

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Services must change state and publish events atomically. Dual writes lose or invent events on partial failure.

## 2. Options
1. Dual write with retries
2. Transactional outbox + polling publisher
3. Transactional outbox + Debezium CDC
4. Event sourcing

## 3. Decision
Outbox table in each service schema, written in the same transaction as the state change. A single leader-elected (ShedLock) polling publisher per service reads in order and publishes with an idempotent producer. At-least-once delivery; consumers deduplicate by eventId.

## 4. Trade-offs
Polling adds up to ~200 ms latency and DB load (indexed partial scan). A single publisher caps throughput (thousands/s, far above control-plane rates). Debezium would remove polling but adds Kafka Connect and replication-slot operations. It is the documented upgrade path.

## 5. Scaling implications
Throughput bounded by one publisher per service; shard by aggregate hash if ever needed.

## 6. Failure modes
Kafka down: rows accumulate, nothing is lost. Publisher crash: the lease expires and another instance takes over. Duplicates on crash-after-send are handled by consumer idempotency.

## 7. Security implications
Audit events use the same path, so audit cannot be skipped by a Kafka outage.

## 8. Operational implications
Alert on oldest unpublished age > 60 s; purge published rows after 3 days.

## 9. Cost implications
Negligible: one small table and one scheduled task per service.
