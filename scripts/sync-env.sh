#!/usr/bin/env bash
# Creates .env from .env.example, or appends keys that .env is missing. Existing values are never
# changed. Placeholders "__GENERATE_BASE64_32__" are replaced with fresh random 256-bit keys.
set -euo pipefail

EXAMPLE=.env.example
TARGET=.env
PLACEHOLDER=__GENERATE_BASE64_32__

touch "$TARGET"
chmod 600 "$TARGET"
added=0
while IFS= read -r line; do
  [[ "$line" =~ ^([A-Z0-9_]+)=(.*)$ ]] || continue
  key="${BASH_REMATCH[1]}"
  value="${BASH_REMATCH[2]}"
  grep -qE "^${key}=" "$TARGET" && continue
  if [[ "$value" == "$PLACEHOLDER" ]]; then
    value="$(openssl rand -base64 32)"
  fi
  printf '%s=%s\n' "$key" "$value" >> "$TARGET"
  added=$((added + 1))
done < "$EXAMPLE"
echo "$TARGET: $added setting(s) added; existing values unchanged"
