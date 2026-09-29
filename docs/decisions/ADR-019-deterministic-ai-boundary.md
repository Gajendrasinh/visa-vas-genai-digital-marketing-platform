# ADR-019: Deterministic business rules vs AI responsibilities

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
LLMs are non-deterministic and can be manipulated; financial eligibility, pricing, compliance status and metrics must be exact and explainable.

## 2. Options
1. Let the agent decide with guidance
2. Hard boundary: code decides, AI explains/generates

## 3. Decision
Hard boundary (architecture.md §3). Every AI output that states a fact is validated against deterministic sources; no AI tool can change eligibility, pricing, approval or campaign status.

## 4. Trade-offs
Less 'magical' agent behavior; more deterministic code to write (insight engine, rule engine).

## 5. Scaling implications
Deterministic paths are cheap and scale linearly.

## 6. Failure modes
AI failure never corrupts business state.

## 7. Security implications
Removes the largest class of AI-driven authorization and integrity risks.

## 8. Operational implications
Decisions are auditable with reason codes.

## 9. Cost implications
Fewer LLM calls on hot paths.
