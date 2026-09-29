# ADR-011: Java 25 and Spring Boot 4

- **Status:** Accepted
- **Date:** 2026-09-29

## 1. Problem
The brief lists both 'Java 17+/Spring Boot 3+' and 'Java 25+/Spring Boot 4+'.

## 2. Options
1. Java 21 + Boot 3.5
2. Java 25 + Boot 4.x

## 3. Decision
Java 25 (current LTS, installed locally) and Spring Boot 4.0.x (Framework 7, Jackson 3) with Spring Cloud 2025.1.x, the newest GA release train. Boot 4.1 waits for a GA Spring Cloud 2026.0. Virtual threads for blocking I/O fan-out. No preview features (e.g. structured concurrency remains preview), so upgrades stay safe.

## 4. Trade-offs
Boot 4 is newer; some third-party libraries may lag (springdoc 3.x and the MCP Java SDK 2.x are verified in Phase 1). If a critical library is not ready, the fallback is Boot 3.5 on Java 25 for that service only, documented.

## 5. Scaling implications
Virtual threads raise I/O concurrency without reactive code.

## 6. Failure modes
Pinned-carrier issues are largely resolved on 24+; still monitored via JFR events.

## 7. Security implications
Current LTS JDK with security patches.

## 8. Operational implications
Temurin 25 base images.

## 9. Cost implications
None.
