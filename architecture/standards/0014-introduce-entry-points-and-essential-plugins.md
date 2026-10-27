# ADR-0014 - Introduce an entry points architecture module and an essential plugins platform

## Status

- PROPOSED on 2026-10-27

## Context

[ADR-0005](0005-introduce-core-ui-architecture-module.md) decided to introduce a "UI" architecture module to the core platform.
[ADR-0013](0013-introduce-observability-architecture-module.md) replaces it for the services that report what happens during a build.
It leaves user prompting, build options, the console and the CLI where they are until a separate decision places them.

These services are how a user starts and talks to Gradle.
The TIDE team is responsible for them, together with the Tooling API.
Today they are spread across two places:

- The CLI, the wrapper, build options and `start-parameter` live in the core runtime architecture module.
- The console and rich console live in the `logging` project, user input handling lives in `daemon-services`, and the console part of the launcher lives in `launcher`.
- The Tooling API lives in the IDE architecture module.

Their dependencies run in both directions with the core runtime architecture module.
`logging` depends on `cli`, `build-option` and `wrapper-shared`.
The core platform uses `build-option`, `start-parameter` and `tooling-api`.
`launcher` and `tooling-api-builders` use most of the codebase.
A platform built on top of core cannot hold these projects without many violations of the platform boundaries that `PlatformBoundariesTest` checks.

The TIDE team also owns three plugins that every build can use, whatever it builds:

- The Wrapper plugin, in `build-init` today.
- The Help plugin, in `base-diagnostics` today.
- The infrastructure of the Build Init plugin and its basic template, in `build-init` today.

These plugins need only the core platform, once the Build Init templates for the JVM and native software are split out.
Gradle applies them to every build through `ProjectConfigureAction` services, so the core platform does not depend on them.

## Decision

### Entry points

Introduce an "entry points" architecture module to the core platform, in `platforms/entry-points`, owned by the TIDE team.
It sits next to the core runtime, core configuration and core execution architecture modules.

It holds the ways a user starts and talks to Gradle:

- The CLI and the command line options: `cli`, `gradle-cli`, `gradle-cli-main`, `build-option` and `start-parameter`.
- The wrapper: `wrapper-main` and `wrapper-shared`.
- The console and rich console.
- User input handling.
- The console part of the launcher.
- The Tooling API: `tooling-api`, `tooling-api-builders` and `tooling-api-provider`.

Starting and talking to the daemon stays in the core runtime architecture module and with the Cortex team.
This covers the daemon client in `client-services` and the daemon parts of `launcher`.

The module is part of the core platform, so the two-way dependencies with the core runtime stay inside one platform.

This ADR, together with ADR-0013, replaces ADR-0005.

### Essential plugins

Introduce an "essential plugins" platform, in `platforms/essential-plugins`, owned by the TIDE team.
It holds the plugins that every build can use, whatever it builds:

- The Wrapper plugin and task.
- The Help plugin. The `projects` and `properties` reports stay in the core configuration architecture module with the Cortex team.
- The infrastructure of the Build Init plugin and its basic template.

The platform uses the core platform.
Platforms that use these plugins declare that they use the essential plugins platform.

The Build Init templates for plugin development, the JVM and native software move to the plugin development, JVM and native platforms.

## Consequences

- Move the CLI, wrapper, build option and `start-parameter` projects to the entry points module first. They stay in the core platform, so the platform boundaries do not change.
- Move the Tooling API projects in a change of their own.
- Split the console and rich console out of `logging`, user input handling out of `daemon-services`, and the console part of the launcher out of `launcher`, each in a change of its own.
- Create the essential plugins platform by splitting the Wrapper plugin out of `build-init`. Split the Help plugin and the Build Init infrastructure out in later changes.
- Mark ADR-0005 as replaced by ADR-0013 and this ADR once both are accepted.
