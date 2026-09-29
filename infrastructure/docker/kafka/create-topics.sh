#!/usr/bin/env bash
# Idempotently creates the topic catalog. Auto-creation is disabled on the broker, so a typo in a
# topic name fails loudly instead of silently creating a new topic.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
TOPICS_BIN=/opt/kafka/bin/kafka-topics.sh

while read -r name partitions configs; do
  [[ -z "$name" || "$name" == \#* ]] && continue
  args=()
  for c in $configs; do args+=(--config "$c"); done
  "$TOPICS_BIN" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$name" --partitions "$partitions" --replication-factor 1 "${args[@]}"
done < /topics/topics.txt

echo "Topic catalog applied:"
"$TOPICS_BIN" --bootstrap-server "$BOOTSTRAP" --list
