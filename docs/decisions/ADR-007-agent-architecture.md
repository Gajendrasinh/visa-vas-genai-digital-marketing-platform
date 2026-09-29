# ADR-007: Controlled graph-based agents with LangGraph (Python)

- **Status:** Accepted (revised 2026-09-29 after Phase 0 review; originally proposed Java + LangGraph4j)
- **Date:** 2026-09-29

## 1. Problem
The copilot and campaign generator need multi-step tool use, human-in-the-loop interrupts, durable state, bounded cost and auditability. The owner asked for "whichever is best for an AI application".

## 2. Options
1. Free-running ReAct agent
2. Java graph orchestration (LangGraph4j 1.9) in the Java stack
3. **Python LangGraph (1.x) service** with LangChain core interfaces, calling Java services through MCP/HTTP
4. Hand-written state machine

## 3. Decision
Option 3. The ai-orchestrator is a Python service (FastAPI + LangGraph):
- explicit `StateGraph`s with deterministic conditional edges; the LLM is used only at classification, argument-extraction and generation nodes;
- the Postgres checkpointer (`langgraph-checkpoint-postgres`) for interrupts (clarifying questions) and resume after restarts;
- hard budgets (max 8 tool calls, 3 generations, 1 repair, wall-clock limit) enforced in graph state;
- a LangChain `BaseChatModel` adapter that calls **ai-gateway** with prompt references (the orchestrator holds no provider keys);
- tools loaded from the Java MCP servers through the MCP Python client, with the user's token-exchanged credentials.

## 4. Trade-offs
- **For Python:** LangGraph is the reference implementation (interrupts, checkpointers, subgraphs, streaming), the MCP Python SDK is first-class, and the evaluation tooling (judges, IR metrics, dataset tooling) is far richer. Most AI engineering examples and hires are Python.
- **Against:** a second language (build, security libraries, container images, observability setup). Mitigated by confining Python to two services behind OpenAPI/MCP contracts, and by keeping every deterministic and security-critical decision in Java (ADR-019, ADR-022).
- Less "autonomous" than ReAct, by design.

## 5. Scaling implications
Async (asyncio) workers, stateless apart from checkpoints in Postgres and short-term memory in Redis. Scales horizontally on concurrent runs. Uvicorn workers are sized per CPU.

## 6. Failure modes
LLM failure → degraded response with deterministic facts. Tool failure → partial answer naming the failed tool. Pod crash → resume from the last checkpoint. ai-gateway down → deterministic-only intents still answer (facts tables); generation intents return 503 with a template suggestion.

## 7. Security implications
The orchestrator reaches business data only through MCP with down-scoped user tokens. No side-effecting tools beyond drafts. Content guardrails are enforced in ai-gateway, so they cannot be bypassed by orchestrator code paths.

## 8. Operational implications
Separate Python CI job (ruff, mypy strict, pytest, pip-audit), shared OpenTelemetry conventions, and the same Helm library chart.

## 9. Cost implications
Bounded graphs make cost per run predictable and cappable. There is no infrastructure cost difference.
