# ADR-0005 - Introduce a UI architecture module to the core platform

## Date

2024-02-07

## Context

The Gradle core platform provides many services to the Gradle platforms and builds logic. One such group of services allows logic to interact with the build user, to provide diagnostics, progress information, prompt for questions, and so on. Currently, these services are part of the core platform runtime architecture module.

A downside of this structure is that it is difficult to do focused work on the Gradle UI.

## Decision

Introduce a "UI" architecture module to the core platform, and move the user interaction services to this new module.

This includes:

- Logging and progress services.
- Problem generation services (aka the "problems API").
- User prompting services.
- Build options infrastructure.
- The console and CLI, as a specific implementation of these services.

The UI architecture module uses the core runtime architecture module. It provides APIs that are used by the other modules and platforms. 

This ADR does not specify the owner of this new architecture module. However, as a separate module, it can be assigned ownership independently of the other core services.

## Status

ACCEPTED

## Consequences

- Introduce the module and move the services and their implementations.
- Assign ownership of the module.
