# ADR-020: AI evaluation as a CI quality gate

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Prompt, model and retrieval changes can silently degrade quality or safety.

## 2. Options
1. Manual spot checks
2. Offline eval dashboards only
3. Golden-dataset evaluation gating merges, plus nightly and online evaluation

## 3. Decision
Golden datasets in git; deterministic suites always run; live-model suites run on AI-relevant changes in a protected job; baseline comparison with floors and tolerances; judge-model agreement measured against human labels; baselines changed only by AI_OPERATOR approval.

## 4. Trade-offs
LLM-judged metrics are noisy: mitigated by repeated sampling and bootstrap confidence intervals. Live eval costs money: capped per run.

## 5. Scaling implications
Dataset grows over time; runs parallelized.

## 6. Failure modes
Provider outage during CI → job marked 'inconclusive' (merge blocked for AI changes, not for others).

## 7. Security implications
Adversarial suite blocks merges that raise attack success.

## 8. Operational implications
Reports as PR comments; trends on the AI evaluation dashboard.

## 9. Cost implications
Bounded per run; nightly cost tracked.
