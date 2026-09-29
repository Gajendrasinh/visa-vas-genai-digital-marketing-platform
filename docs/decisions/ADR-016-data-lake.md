# ADR-016: S3 data lake with raw and curated zones

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Historical analysis, feature backfill, reconciliation of streaming results and long-term audit need cheap durable storage.

## 2. Options
1. Warehouse only (Redshift)
2. S3 lake (Parquet) + Athena/Glue, Redshift optional
3. Lakehouse table format (Iceberg)

## 3. Decision
Kafka Connect S3 sink → raw Parquet; curation job (DuckDB locally, Athena/Glue in AWS) → curated zone with data-quality checks, quarantine and lineage manifests. Iceberg is a documented next step for schema evolution and compaction.

## 4. Trade-offs
No ACID tables yet (plain Parquet partitions); acceptable for append-only data with idempotent daily rewrites.

## 5. Scaling implications
S3 scales without limit; partition by date/hour.

## 6. Failure modes
Connect sink failure → lag grows, Kafka retention (7 d) buffers; resume from offsets.

## 7. Security implications
Bucket policies, KMS, no PII in lake, Object Lock for audit.

## 8. Operational implications
DQ metrics and alerting; lifecycle policies.

## 9. Cost implications
S3 + Athena pay-per-query is cheap at this scale; Redshift off by default.
