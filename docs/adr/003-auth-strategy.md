# ADR-003: Authentication strategy

## Status

Accepted

## Context

NexusOps has human users, Linux agents and communication between backend services. These identities have different security requirements.

Authentication should not be implemented separately by each application.

## Decision

Human users will authenticate through Keycloak using OIDC/OAuth2.

The web console will use Authorization Code with PKCE, and backend services will validate access tokens and enforce roles.

SentinelAgent will use per-agent certificates and mTLS when communicating with OpsSight.

Service-to-service authentication will use dedicated service identities where required.

## Consequences

Authentication is centralized for human users and backend services do not manage passwords.

Authorization must still be enforced by each backend service.

Agent identity remains separate from human user identity.