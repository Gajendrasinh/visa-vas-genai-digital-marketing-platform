# ADR-013: Avro with Schema Registry

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Events evolve independently of consumers; breaking changes must be caught before deploy.

## 2. Options
1. JSON without schema
2. JSON Schema + registry
3. Protobuf + registry
4. Avro + registry

## 3. Decision
Avro with a Confluent-compatible Schema Registry, TopicNameStrategy with one record type per topic (event kind as an enum with a default; state snapshot carried), BACKWARD_TRANSITIVE compatibility, and a CI compatibility check.

## 4. Trade-offs
Binary payloads are less readable (tooling needed). Avro has the best Kafka Connect and Parquet interoperability for the lake.

## 5. Scaling implications
Registry is a small stateless service backed by a Kafka topic.

## 6. Failure modes
Registry down: producers and consumers use cached schemas; new schemas can't register until it is back.

## 7. Security implications
Registry write access only from CI; services have read-only credentials.

## 8. Operational implications
Schema changes are reviewed via PR.

## 9. Cost implications
Negligible.
