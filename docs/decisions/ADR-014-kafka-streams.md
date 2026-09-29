# ADR-014: Kafka Streams for real-time processing

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Per-customer features, attribution joins and campaign aggregates need event-time windows, late-data handling, deduplication and exactly-once semantics.

## 2. Options
1. Apache Flink (Amazon Managed Service for Apache Flink)
2. Kafka Streams
3. Spark Structured Streaming
4. Plain consumers + DB

## 3. Decision
Kafka Streams with EOS v2, custom daily-bucket processor for rolling features, windowed joins for attribution, and two-stage aggregation for hot campaign keys.

## 4. Trade-offs
Flink has richer event-time semantics and a larger state capacity, but it requires a separate cluster and deployment model. Kafka Streams runs as a normal Java service on EKS. Revisit if state size or window complexity outgrows it.

## 5. Scaling implications
Scales to the partition count; state is sharded by key with standby replicas for fast failover.

## 6. Failure modes
Instance loss → task migration with state restore from changelog (standby replicas shorten it). Late events beyond grace → side topic + nightly batch correction.

## 7. Security implications
Same Kafka IAM model; state stores on encrypted volumes.

## 8. Operational implications
Monitor restore time, commit latency, and punctuation lag.

## 9. Cost implications
Local disk for RocksDB; no extra cluster cost.
