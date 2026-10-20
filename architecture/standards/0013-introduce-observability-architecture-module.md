# ADR-0013 - Introduce an observability architecture module

## Status

- PROPOSED on 2026-10-20

## Context

[ADR-0005](0005-introduce-core-ui-architecture-module.md) decided to introduce a "UI" architecture module to the core platform, holding the services that let build logic interact with the build user:

- Logging and progress services.
- Problem generation services (aka the "problems API").
- User prompting services.
- Build options infrastructure.
- The console and CLI, as a specific implementation of these services.

The module was never introduced. The Problems API projects live in the IDE architecture module, and the other services live in the core runtime architecture module.

The TIDE team is responsible for these services and is grouping its projects into architecture modules by what they do.
The services that report what happens during a build (logging, progress, problems, build operations) form one group.
The ways a user starts and talks to Gradle (the CLI, the console, user prompts, build options) form another.

## Decision

Introduce an "observability" architecture module, in `platforms/observability`, owned by the TIDE team.

It holds the services that report what happens during a build:

- Problem generation and reporting services (the Problems API) and their implementations.
- Logging and progress services.
- Build operations.
- The infrastructure for rendering reports.

The module is a top-level architecture module, like the IDE module. Most of the codebase uses it, and it uses the core runtime architecture module.

This ADR replaces ADR-0005 for the services it lists. User prompting, build options, the console and the CLI are outside this ADR's scope. They stay where they are until a separate decision places them.

## Consequences

- Introduce the observability module and move the Problems API projects to it first.
- Move the logging, build operations and report rendering projects in later changes, with the agreement of their current owners.
- Mark ADR-0005 as replaced by this ADR once this ADR is accepted.
