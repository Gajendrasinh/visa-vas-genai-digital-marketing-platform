# ADR-002: Apache Kafka as the event backbone

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Behavioral and payment signals arrive at high volume and must drive features, segmentation and offers asynchronously, with replay, ordering per customer and multiple independent consumers.

## 2. Options
1. Amazon SQS/SNS
2. Amazon Kinesis
3. Apache Kafka (Amazon MSK in AWS)
4. RabbitMQ

## 3. Decision
Apache Kafka: KRaft locally, MSK in AWS. Keyed partitions give per-customer ordering. Retention and consumer groups allow replay and independent consumers. Kafka Streams provides stateful processing without a separate cluster.

## 4. Trade-offs
Operationally heavier than SQS. Partition counts are hard to change later. Mitigated by MSK and up-front sizing (kafka.md §2.1).

## 5. Scaling implications
Horizontal via partitions (24 for customer-keyed topics); consumers scale up to the partition count.

## 6. Failure modes
Broker loss is tolerated with RF=3/min.insync=2. Full outage: outbox rows buffer in Postgres; ingestion sheds load with 503 + Retry-After.

## 7. Security implications
MSK IAM auth with per-topic permissions, TLS, no PII in payloads.

## 8. Operational implications
Consumer lag, DLQ and outbox age are first-class alerts. A replay tool is provided.

## 9. Cost implications
MSK is the largest fixed infra cost. Dev can use MSK Serverless or small brokers. Local is a single broker.
