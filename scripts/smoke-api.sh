#!/usr/bin/env bash
# End-to-end smoke test through the real stack: Keycloak token -> api-gateway -> campaign-service
# -> PostgreSQL. Requires `make up-core` and the gateway (8080) and campaign-service (8084) running.
set -euo pipefail

set -a
# shellcheck disable=SC1091
source .env
set +a

KEYCLOAK="${KEYCLOAK_URL:-http://localhost:8180}/realms/vasmkt/protocol/openid-connect/token"
GATEWAY="${GATEWAY_URL:-http://localhost:8080}"

token() {
  curl -fsS -d grant_type=password -d client_id=vasmkt-local-cli -d "username=$1" \
    --data-urlencode "password=${KEYCLOAK_DEMO_USER_PASSWORD}" "$KEYCLOAK" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}

claims() {
  python3 -c 'import base64,json,sys; p=sys.argv[1].split(".")[1]; p+="="*(-len(p)%4); c=json.loads(base64.urlsafe_b64decode(p)); print(c.get("tenant_id"), c.get("aud"), [r for r in c["realm_access"]["roles"] if r.isupper()])' "$1"
}

call() { # method path token [body] -> prints "status body"
  local method=$1 path=$2 tok=$3 body=${4:-}
  curl -sS -o /tmp/smoke-body.$$ -w '%{http_code}' -X "$method" "$GATEWAY$path" \
    -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $(uuidgen)" ${body:+-d "$body"}
  echo " $(cat /tmp/smoke-body.$$)"
  rm -f /tmp/smoke-body.$$
}

expect() { # expected actual label
  if [[ "$2" == "$1"* ]]; then echo "PASS $3 ($1)"; else echo "FAIL $3: expected $1, got ${2:0:300}"; exit 1; fi
}

json() { python3 -c "import json,sys; print(json.loads(sys.argv[1].split(' ',1)[1])$2)" "$1"; }

MANAGER=$(token manager); APPROVER=$(token approver); BETA=$(token manager-beta)
echo "manager token claims:  $(claims "$MANAGER")"
echo "beta token claims:     $(claims "$BETA")"

expect 401 "$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/v1/campaigns")" "no token rejected at gateway"

START=$(date -u -v+1H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+1 hour' +%Y-%m-%dT%H:%M:%SZ)
END=$(date -u -v+30d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+30 days' +%Y-%m-%dT%H:%M:%SZ)
CAMPAIGN="{\"name\":\"Smoke $(uuidgen)\",\"objective\":\"Smoke test\",\"segmentId\":\"$(uuidgen)\",\"offerId\":\"$(uuidgen)\",\"startAt\":\"$START\",\"endAt\":\"$END\",\"budget\":{\"amount\":\"1000\",\"currency\":\"USD\"},\"channels\":[\"EMAIL\"]}"

R=$(call POST /api/v1/campaigns "$MANAGER" "$CAMPAIGN"); expect 201 "$R" "manager creates campaign"
ID=$(json "$R" "['id']")
R=$(call POST "/api/v1/campaigns/$ID/variants" "$MANAGER" '{"channel":"EMAIL","variantKey":"A","language":"en","subject":"{{offer.value}} back","bodyTemplate":"Get {{offer.value}} back at {{merchant.name}}.","generatedBy":"HUMAN"}')
expect 201 "$R" "manager adds variant"
VARIANT=$(json "$R" "['variantId']")
expect 200 "$(call POST "/api/v1/campaigns/$ID/variants/$VARIANT/compliance-review" "$MANAGER" '{"passed":true,"report":"smoke"}')" "compliance recorded"
expect 200 "$(call POST "/api/v1/campaigns/$ID/submit" "$MANAGER")" "submitted"
expect 403 "$(call POST "/api/v1/campaigns/$ID/approvals" "$MANAGER" '{"decision":"APPROVE"}')" "creator cannot approve (four-eyes)"
expect 200 "$(call POST "/api/v1/campaigns/$ID/approvals" "$APPROVER" '{"decision":"APPROVE"}')" "approver approves"
R=$(call POST "/api/v1/campaigns/$ID/publish" "$APPROVER"); expect 200 "$R" "published"
echo "     status: $(json "$R" "['status']")"
expect 404 "$(call GET "/api/v1/campaigns/$ID" "$BETA")" "other tenant sees 404"
echo "Smoke test passed."
