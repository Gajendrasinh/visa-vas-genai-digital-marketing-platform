#!/usr/bin/env bash
# LocalStack ready hook (ADR-023): recreates the AWS resources the services expect.
# Mirrors infrastructure/terraform for S3, KMS and Secrets Manager. Values are local placeholders.
set -euo pipefail

ENV_NAME="${PLATFORM_ENV:-local}"
PREFIX="vasmkt-${ENV_NAME}"

for bucket in lake knowledge; do
  awslocal s3api create-bucket --bucket "${PREFIX}-${bucket}" >/dev/null
  awslocal s3api put-bucket-encryption --bucket "${PREFIX}-${bucket}" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"aws:kms"}}]}'
done

# Audit archive: Object Lock must be enabled at creation time.
awslocal s3api create-bucket --bucket "${PREFIX}-audit-archive" \
  --object-lock-enabled-for-bucket >/dev/null

key_id=$(awslocal kms create-key --description "PII envelope encryption (${ENV_NAME})" \
  --query KeyMetadata.KeyId --output text)
awslocal kms create-alias --alias-name "alias/${PREFIX}-pii" --target-key-id "$key_id"

awslocal secretsmanager create-secret --name "${PREFIX}/ai-gateway/providers" \
  --secret-string '{"anthropicApiKey":"placeholder","openaiApiKey":"placeholder"}' >/dev/null

echo "LocalStack resources ready for ${PREFIX}"
