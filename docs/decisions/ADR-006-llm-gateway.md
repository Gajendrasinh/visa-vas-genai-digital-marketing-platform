# ADR-006: Own AI gateway with provider adapters

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Applications must not couple to one model provider and need central routing, fallback, cost tracking, prompt versioning and data-egress control.

## 2. Options
1. Call provider SDKs directly from each service
2. Framework abstraction (e.g. Spring AI) in each service
3. Central internal ai-gateway service wrapping official provider SDKs
4. Third-party LLM proxy (e.g. LiteLLM)

## 3. Decision
Central ai-gateway (Java) with adapters over official SDKs (Anthropic direct/Bedrock, OpenAI, OpenAI-compatible local, deterministic stub). Callers send prompt references + classified variables, never raw prompts.

## 4. Trade-offs
One extra hop (~1–3 ms in-cluster) on every AI call. The gateway is a critical dependency, so it runs ≥3 replicas with a PDB. Using official SDKs rather than another abstraction layer keeps provider features (prompt caching, batch, token counts) available.

## 5. Scaling implications
Stateless; scales on concurrency. Bulkheads per provider.

## 6. Failure modes
Provider outage → router fallback → deterministic fallback in the caller. Gateway outage → callers' circuit breakers → deterministic paths.

## 7. Security implications
The single holder of LLM credentials. It enforces the egress policy and PII redaction centrally.

## 8. Operational implications
Single place for token/cost/latency telemetry and prompt version rollout.

## 9. Cost implications
Enables cost controls (routing, caching, budgets) that would otherwise be duplicated per service.
