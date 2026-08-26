#!/bin/sh

set -eu

: "${KAFKA_BOOTSTRAP_SERVERS:?KAFKA_BOOTSTRAP_SERVERS is required}"
: "${NEXUSOPS_KAFKA_ALERT_TOPIC:?NEXUSOPS_KAFKA_ALERT_TOPIC is required}"
: "${NEXUSOPS_KAFKA_INCIDENT_TOPIC:?NEXUSOPS_KAFKA_INCIDENT_TOPIC is required}"

kafka_topics="/opt/kafka/bin/kafka-topics.sh"

for topic in \
    "$NEXUSOPS_KAFKA_ALERT_TOPIC" \
    "$NEXUSOPS_KAFKA_INCIDENT_TOPIC"
do
    "$kafka_topics" \
        --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
        --create \
        --if-not-exists \
        --topic "$topic" \
        --partitions 1 \
        --replication-factor 1
done

"$kafka_topics" \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --list