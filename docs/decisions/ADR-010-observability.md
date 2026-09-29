# ADR-010: OpenTelemetry-based observability

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Requests cross HTTP, Kafka and LLM boundaries; AI adds token, cost and quality signals.

## 2. Options
1. Vendor APM agent
2. OpenTelemetry (Micrometer + OTLP) with pluggable backends

## 3. Decision
Micrometer observation + OTLP to an OTel Collector; locally Prometheus/Tempo/Loki/Grafana, in AWS ADOT to CloudWatch/AMP/X-Ray. Trace context in Kafka headers and outbox rows. GenAI semantic conventions for AI spans. JSON structured logs without prompt or PII content.

## 4. Trade-offs
Self-managed stack locally; more config than a vendor agent, but no lock-in and identical instrumentation everywhere.

## 5. Scaling implications
Tail-based sampling at the collector (keep errors and slow traces at 100 %, sample the rest).

## 6. Failure modes
Telemetry pipeline failure never blocks requests (async exporters with bounded queues).

## 7. Security implications
Log/trace attribute allow-lists; no prompt content; PII masking.

## 8. Operational implications
Dashboards and alerts as code (Grafana JSON / Prometheus rules) in the repo.

## 9. Cost implications
Sampling and retention settings control cost; CloudWatch log retention set per environment.
