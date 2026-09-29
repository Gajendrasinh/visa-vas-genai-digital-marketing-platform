# ADR-008: MCP as the only AI-to-business-data interface

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Internal agents and external AI clients need a governed, uniform way to use business capabilities.

## 2. Options
1. Direct in-process tool functions calling services
2. MCP servers as the tool boundary for both internal and external agents

## 3. Decision
Two MCP servers (marketing, customer) using the official MCP Java SDK over Streamable HTTP, acting as OAuth 2.1 resource servers. The orchestrator is an MCP client like any other.

## 4. Trade-offs
An extra hop for internal tool calls (low ms). In return there is one enforcement point for schema, authorization, rate limits and audit, and external clients get the same controls.

## 5. Scaling implications
Stateless servers; scale horizontally.

## 6. Failure modes
MCP server down → affected intents degrade. Other intents still work (knowledge QA uses rag-service directly).

## 7. Security implications
No token passthrough (confused deputy); audience-bound tokens; per-client allow-lists; static reviewed tool descriptions; customer data isolated in its own server.

## 8. Operational implications
Tool catalog versioned; tool usage metrics and denials on the AI dashboard.

## 9. Cost implications
Minimal.
