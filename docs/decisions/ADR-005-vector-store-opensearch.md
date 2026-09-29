# ADR-005: OpenSearch as search and vector store

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
RAG needs keyword search, vector search, hybrid fusion and metadata filtering at low latency, available both locally and on AWS.

## 2. Options
1. pgvector in PostgreSQL
2. OpenSearch (k-NN + BM25 + hybrid search pipeline)
3. Dedicated vector DB (e.g. a managed SaaS)

## 3. Decision
OpenSearch, with Postgres FTS over authoritative chunks as the fallback.

## 4. Trade-offs
pgvector would mean one less system and is adequate at this corpus size, but its BM25-quality text ranking and hybrid fusion are weaker, and heavy ANN queries would compete with OLTP. A SaaS vector DB adds a third-party data processor for policy content. OpenSearch is a managed AWS service and runs locally.

## 5. Scaling implications
Shards and replicas; the corpus (thousands of chunks) is small, so the headroom is large.

## 6. Failure modes
Node loss is covered by replicas. Full outage uses keyword fallback with a flag.

## 7. Security implications
Fine-grained access control + VPC-only domain + KMS. Tenant/role filters are applied by rag-service on every query (the index itself is not user-facing).

## 8. Operational implications
Index templates and aliases managed as code; snapshot to S3.

## 9. Cost implications
Smallest prod-grade domain is a few hundred USD/month. Dev is a single node.
