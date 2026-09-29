# ADR-017: Keycloak for identity with token exchange

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
The platform needs OIDC for humans, client credentials for services, fine-grained roles, and delegated, down-scoped tokens for agents.

## 2. Options
1. Amazon Cognito
2. Keycloak
3. Custom identity service
4. Commercial IdP

## 3. Decision
Keycloak (containers locally, HA on EKS) with realm roles, per-service clients, and RFC 8693 token exchange for agent-on-behalf-of-user calls.

## 4. Trade-offs
Self-hosting an IdP is operational work. Cognito lacks standard token exchange for this pattern. A custom identity service is ruled out on security grounds.

## 5. Scaling implications
Stateless nodes + Postgres; cache JWKS in services.

## 6. Failure modes
IdP outage: existing tokens remain valid until expiry (5 min); new logins fail; alert immediately.

## 7. Security implications
Brute-force protection, MFA for admin roles, short token TTLs, private-key JWT for service clients in prod.

## 8. Operational implications
Realm config as code (exported JSON) in the repo.

## 9. Cost implications
A few small pods.
