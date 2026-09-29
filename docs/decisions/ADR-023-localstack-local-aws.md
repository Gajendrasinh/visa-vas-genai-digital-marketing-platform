# ADR-023: LocalStack for local AWS APIs

- **Status:** Accepted
- **Date:** 2026-09-29

## 1. Problem
Code that uses S3, KMS and Secrets Manager must run locally and in CI without an AWS account, and Terraform for those resources should be exercised, not just validated.

## 2. Options
1. MinIO for S3 only, local key files for KMS/secrets
2. LocalStack for AWS APIs + real containers for data engines
3. LocalStack Pro emulating MSK, RDS, ElastiCache and OpenSearch too

## 3. Decision
Option 2. LocalStack provides S3 (lake, RAG originals, audit archive), KMS (envelope encryption of PII), Secrets Manager (service secrets) and CloudWatch Logs. The services use the normal AWS SDK with an endpoint override (`AWS_ENDPOINT_URL`), so no code differs between local and AWS. Kafka, PostgreSQL, Redis, OpenSearch and Keycloak run as real containers. `tflocal` applies the S3/KMS/Secrets Terraform modules against LocalStack.

## 4. Trade-offs
Since March 2026 LocalStack requires an auth token (`LOCALSTACK_AUTH_TOKEN`; the free Hobby plan is for non-commercial use and covers the former community services). Without a token, S3 falls back to the `s3-compatible` profile. IAM policies are not enforced by default, so IAM correctness is verified by checkov/plan review, not locally.

## 5. Scaling implications
Local only.

## 6. Failure modes
LocalStack state is ephemeral by default: an init hook recreates buckets, keys and secrets on start.

## 7. Security implications
Only placeholder secrets are used locally. The auth token lives in `.env` (git-ignored).

## 8. Operational implications
`make up-core` starts LocalStack with the rest of the core profile. The same init scripts double as documentation of the AWS resources.

## 9. Cost implications
Free tier for non-commercial use. A commercial use of this repository requires a LocalStack plan or the MinIO profile.
