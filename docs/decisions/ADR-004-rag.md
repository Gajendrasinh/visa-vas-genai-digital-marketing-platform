# ADR-004: Retrieval-augmented generation design

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
AI answers about policies, offers and brand rules must be grounded in approved, versioned, access-controlled documents, with citations.

## 2. Options
1. Long-context stuffing of whole documents
2. Vector-only RAG
3. Hybrid (BM25 + vector) with reranking, ACL filtering and thresholds
4. Fine-tuning

## 3. Decision
Option 3. Structure-aware chunking; Postgres holds the authoritative chunks; OpenSearch holds BM25 + HNSW indices; authorization is a pre-filter inside the query; cross-encoder rerank; relevance threshold with an explicit 'no grounded answer' path; citations validated. In-process ONNX embeddings/reranker by default, provider embeddings optional.

## 4. Trade-offs
More moving parts than vector-only. Hybrid handles exact terms (offer codes, MCC names) that pure vectors miss. Fine-tuning rejected: it cannot cite, version or enforce ACLs.

## 5. Scaling implications
Index per embedding model with alias swap; OpenSearch scales by shards/replicas; ONNX inference scales with rag-service pods.

## 6. Failure modes
OpenSearch down → Postgres FTS fallback. Embedding provider down → local model (index must match, so prod pins one model per index).

## 7. Security implications
ACL pre-filtering prevents leakage via ranking. Ingestion-time injection scan. Documents writable only by admin roles.

## 8. Operational implications
Re-embedding runbook; retrieval metrics tracked in CI.

## 9. Cost implications
In-process embeddings cost CPU, not per-token fees. OpenSearch cluster is the main cost.
