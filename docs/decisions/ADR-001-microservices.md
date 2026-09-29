# ADR-001: Service decomposition: consolidated microservices

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
The platform spans independent change cadences (campaign tooling, streaming, AI) and very different scaling profiles (ingestion vs. control plane). The brief lists ~28 candidate services, and many would own a single table or sit on the hot path of another.

## 2. Options
1. Modular monolith
2. Fine-grained microservices (one per listed capability, ~28)
3. Consolidated microservices by bounded context and scaling profile (~15 + 2 MCP)

## 3. Decision
Option 3. Services map to bounded contexts (customer, segment, offer, campaign, personalization, notification, analytics, audit) plus AI-platform components. Capabilities that share lifecycle and credentials are merged (prompt registry and router into ai-gateway; memory into orchestrator; embeddings into rag-service; merchant into customer-service). The guardrail engine is a module of ai-gateway.

## 4. Trade-offs
More operational surface than a monolith (deploys, network hops, distributed tracing needed). Fewer hops and less duplication than 28 services. Merges are reversible because modules keep hexagonal boundaries.

## 5. Scaling implications
Each service scales on its own axis: ingestion and stream-processor by partitions, orchestrator by concurrent conversations, ai-gateway by provider concurrency.

## 6. Failure modes
Partial failure is contained. The AI platform can be fully down while the deterministic flow continues (P2). Cascading failure is limited by timeouts, circuit breakers and async edges.

## 7. Security implications
Smaller blast radius per credential: only ai-gateway has LLM keys; only customer-service decrypts PII.

## 8. Operational implications
Requires the observability, CI templates and Helm library chart built early. ArchUnit keeps the internal module boundaries honest.

## 9. Cost implications
~17 pods minimum per environment. Dev runs 1 replica each on small nodes. A monolith would be cheaper to run but would not demonstrate the target competencies or isolate AI failures.
