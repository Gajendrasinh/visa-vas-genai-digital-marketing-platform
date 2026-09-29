# ADR-009: Kubernetes on Amazon EKS

- **Status:** Proposed (Phase 0 review)
- **Date:** 2026-09-29

## 1. Problem
~17 services need rolling deploys, autoscaling, multi-AZ placement and consistent config across environments.

## 2. Options
1. ECS Fargate
2. EKS
3. Lambda for parts

## 3. Decision
EKS with a shared Helm library chart; HPA, PDB, topology spread across 3 AZs, IRSA, External Secrets Operator. Kind is used in CI for manifest verification.

## 4. Trade-offs
More complex than ECS. Chosen for portability (kind locally, same charts) and ecosystem (OTel, Linkerd, ESO).

## 5. Scaling implications
HPA on CPU plus custom metrics (Kafka lag via KEDA optional). Cluster autoscaler or Karpenter.

## 6. Failure modes
Node or AZ loss absorbed by spread + PDB. Pods are drained gracefully (preStop + Spring graceful shutdown).

## 7. Security implications
IRSA per pod, network policies default-deny, restricted Pod Security Standard, non-root images.

## 8. Operational implications
GitHub Actions deploys with helm --atomic; rollback on failed readiness.

## 9. Cost implications
Control plane fee + nodes. Dev uses small spot node groups.
