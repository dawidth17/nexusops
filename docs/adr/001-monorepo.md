# ADR-001: Use a monorepo

## Status

Accepted

## Context

NexusOps contains several components written in different languages, but they belong to the same platform and share contracts, infrastructure and documentation.

Using separate repositories would add unnecessary overhead while the project is developed by one person.

## Decision

NexusOps will use a single Git repository.

Each component will remain separated in its own directory:

- `servicecore`
- `opssight`
- `sentinel-agent`
- `console`

Shared contracts, infrastructure and documentation will also be stored in the same repository.

## Consequences

Changes that affect multiple components can be developed and reviewed together.

The repository will become larger as the project grows, so component boundaries must remain clear.