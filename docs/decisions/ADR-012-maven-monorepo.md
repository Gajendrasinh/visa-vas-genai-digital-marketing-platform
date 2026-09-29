# ADR-012: Maven multi-module monorepo

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
Many services share build conventions, versions and technical libraries.

## 2. Options
1. Polyrepo
2. Gradle multi-project monorepo
3. Maven multi-module monorepo

## 3. Decision
Maven with wrapper, parent POM + BOM, enforcer (dependency convergence, banned deps), per-module plugins. CI builds only changed modules plus dependents (`-pl -am`).

## 4. Trade-offs
Gradle builds faster with its build cache; Maven is more common in enterprise Java, needs no bootstrap here, and is declarative. Monorepo keeps cross-cutting changes atomic but needs path-filtered CI.

## 5. Scaling implications
Reactor parallel builds (`-T 1C`) plus CI caching.

## 6. Failure modes
n/a

## 7. Security implications
One place to pin and patch dependencies; dependency review on one lockstep BOM.

## 8. Operational implications
A single version upgrade touches one file.

## 9. Cost implications
CI minutes controlled via change detection.
