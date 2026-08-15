# ADR-004: Use RabbitMQ and Kafka for different purposes

## Status

Accepted

## Context

OpsSight needs background task processing, while OpsSight and ServiceCore also need to exchange domain events.

Using the same messaging system for both purposes would mix two different responsibilities.

## Decision

RabbitMQ will be used with Celery for background and scheduled tasks inside OpsSight.

Kafka will be used for domain events exchanged between OpsSight and ServiceCore.

## Consequences

Each messaging system has a clear role.

This adds infrastructure and operational complexity because both RabbitMQ and Kafka need to be configured and maintained.