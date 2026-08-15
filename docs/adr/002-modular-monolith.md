# ADR-002: Keep ServiceCore as a modular monolith

## Status

Accepted

## Context

ServiceCore contains several related ITSM features such as incidents, SLA management, assets, knowledge and audit.

Splitting these features into separate microservices would add deployment and communication complexity without a current need for independent scaling or ownership.

## Decision

ServiceCore will be built as a modular monolith using Spring Boot.

The application will be separated into modules with clear responsibilities, but it will remain a single deployable application.

## Consequences

Related operations can use normal database transactions and internal module calls.

The application is simpler to develop, test and deploy.

Module boundaries must remain clear so the codebase does not become tightly coupled as it grows.