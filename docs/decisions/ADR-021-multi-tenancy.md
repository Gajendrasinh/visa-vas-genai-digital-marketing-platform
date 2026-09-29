# ADR-021: Multi-tenancy by financial institution

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Campaigns and customer data belong to issuer tenants and must be isolated.

## 2. Options
1. Instance per tenant
2. Schema per tenant
3. Shared tables with tenant_id + enforced filters + RLS on PII

## 3. Decision
Shared tables with tenant_id on every row, tenant from the JWT claim, repository-level enforcement, Postgres RLS on PII tables, tenant filters in OpenSearch and Kafka payloads.

## 4. Trade-offs
Relies on correct enforcement code, so cross-tenant tests are mandatory. Instance-per-tenant isolation is stronger but cost-prohibitive for many tenants.

## 5. Scaling implications
Scales to many tenants; noisy tenants are controlled by rate limits and AI budgets per tenant.

## 6. Failure modes
A tenant filter bug would be serious: defense in depth (RLS, retrieval filters, tool-result tenant check).

## 7. Security implications
Tenant ID is never accepted from request bodies, only from the token.

## 8. Operational implications
Per-tenant dashboards and cost attribution.

## 9. Cost implications
Shared infra keeps per-tenant cost low.
