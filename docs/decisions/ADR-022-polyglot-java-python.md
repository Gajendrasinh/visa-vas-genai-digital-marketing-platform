# ADR-022: Polyglot boundary — Java core, Python for agent orchestration and evaluation

- **Status:** Accepted
- **Date:** 2026-09-29

## 1. Problem
The platform's core (transactions, streaming, eligibility, security) fits the Java ecosystem best, while agent orchestration and AI evaluation tooling are strongest in Python. A single language for everything would weaken one side.

## 2. Options
1. All Java (LangGraph4j, Java eval harness)
2. All Python
3. Java core + Python for ai-orchestrator and evaluation-service only

## 3. Decision
Option 3.

| Java 25 / Spring Boot 4 | Python 3.13 |
|---|---|
| All domain services, event ingestion, stream-processor, ai-gateway (keys, routing, egress policy, **guardrail engine**, cost), rag-service (ingestion + ACL-filtered retrieval), MCP servers, audit | ai-orchestrator (LangGraph agents, memory, A2A), evaluation-service (golden-set runs, IR metrics, LLM judges, CI gate) |

Rule: anything that **decides** (eligibility, authorization, ACL filtering, egress policy, guardrail verdicts, metrics) stays in Java. Python composes, generates and measures.

## 4. Trade-offs
Two toolchains. The integration contracts (OpenAPI for ai-gateway/rag, MCP for tools, Avro for `ai.events`) become more important and are tested by contract tests. Python services use uv, ruff, mypy --strict and pytest to keep quality gates equivalent to Java.

## 5. Scaling implications
Independent: the Python services are stateless async workers.

## 6. Failure modes
Contract drift between languages is caught by contract tests (OpenAPI schema validation in pytest against the Java-generated spec, MCP tool-schema snapshot tests).

## 7. Security implications
Python services hold no LLM keys and no direct database access to domain schemas (only their own `orchestrator`/`evaluation` schemas). JWT validation uses PyJWT against Keycloak JWKS, with the same claims model.

## 8. Operational implications
Shared conventions: JSON logs with the same fields, OTel semantic conventions, `/health/live` and `/health/ready` probes, identical Helm library chart.

## 9. Cost implications
None at runtime. Moderate CI time for the extra job.
