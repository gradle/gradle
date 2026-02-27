# ADR-0010 - Gradle properties naming

## Date

2026-02-26

## Context

[Gradle properties](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties)
are flags that allow build engineers to opt into and out of features of Gradle Runtime or Core Plugins.
Practically every Gradle feature is behind a flag, and additional sub-flags or options provide further control.

The number of existing Gradle properties is already in the double digits. More properties will be added in the future.
Although properties offer the benefit of precise user-side control and support gradual evolution practices, the high number of properties also presents challenges.

The main challenge on the user side is comprehensibility.
With that many properties, and considering the tendency for properties to accrete in user builds over time, it is important for users to understand the contracts tied to the features.
For instance, whether they use an internal property (intentionally or accidentally), or whether they use an unstable feature that can change at any time.

Symmetrically, the same problem exists on the side of Gradle maintainers.
It should be clear when introducing a property what contract users can expect and what we can afford to provide.
As features progress through the lifecycle of stabilization and, possibly later, deprecation, the corresponding properties can also adjust accordingly.

Since the name of the property is the primary source of immediate information for users, both the names themselves and their structure should convey enough information to set the right expectations.

Previously, there was no formalization of property naming; only loose conventions were followed.
While these conventions served relatively well in practice, they created gaps, resulting in a loss of uniformity in some cases and leaving friction in the process of naming new properties.

### Terminology

**Public properties** are formally a part of the Public API.
Public properties are **stable properties**: they CANNOT be renamed or removed without notice.
While we normally deprecate the feature or behavior itself, changes to the property cannot happen in a minor release.
Public properties must be documented.

**Internal properties** are not part of the Public API.
Internal properties are **unstable properties**: they CAN be renamed or removed without notice.
They are not intended for general use.
While they can be helpful in isolated cases of troubleshooting, they are intended mostly for testing or other activities within Gradle development.
The behavior behind the internal property can change at any time and without notice.
Internal properties MUST NOT be documented in public documentation.

As a *secondary aspect*, property names can carry **feature-stability** information of the features they represent.
As features progress through the lifecycle of early prototype, incubation, and stabilization, their corresponding properties may also be updated.

Examples as of Gradle 9.3.0:

* `org.gradle.internal.operations.trace` – internal property
* `org.gradle.configuration-cache` – public property of a stable feature
* `org.gradle.configuration-cache.parallel` – public property of an unstable feature
* `org.gradle.configuration-cache.internal.parallel-store` – internal property
* `org.gradle.unsafe.isolated-projects` — public property of an unstable feature
* `org.gradle.experimental.declarative-common` – public property of an unstable feature

## Decision

New properties will adhere to the explicit naming rules, and existing properties will be updated where possible.

The naming rules are user-centric.
By the name alone, users will be able to determine whether the property (and the corresponding feature) is public or internal, stable or unstable.

Shared scheme

```
org.gradle[.<qualifier>].<feature-name>.<detail>
```

The optional qualifier defines the secondary aspects, if any.

For better ergonomics on the CLI, some properties can have supplementary command-line build option(s).
The name of the feature in the build option SHOULD be the same as in the property name.

Shared scheme for long-form build options:

```
--<feature-name>-<detail>
```

Decisions on the short-form build options should be made on a case by case basis, since the space of available names is small.

### Internal properties

Internal properties MUST start with `org.gradle.internal.`

This clearly signals to users that they are dealing with a non-public property and should expect no guarantees.

Internal properties MUST NOT be supplemented with command-line options.
They can always be passed via `-D` on the command line.

Examples of properties that follow the naming rules:

* `org.gradle.internal.operations.trace`
* `org.gradle.internal.cmdline.max.length`
* …

### Properties of pre-incubation features

Properties of pre-incubation features MUST start with `org.gradle.experimental.`  
They can all be called **experimental properties**.

The qualifier clearly indicates to users that the feature is still in early development and not yet stable.

Experimental properties MUST be stable.
While the corresponding feature can be changed or dropped without notice, users should receive a notice about the property rename or removal.

Experimental properties MUST NOT be supplemented with command-line options.
They can always be passed via `-D` on the command line.
The feature should leave the experimental stage to receive a build option.

The difference between internal properties and experimental properties is the amount of publicity they are intended to receive.
If the intention is to gather user feedback on an early prototype, then the experimental property should be used.
If the intention is to allow an issue reporter to get additional local diagnostics when running a build in their proprietary environment, then the internal property is sufficient.

Examples of properties that follow the naming rules:

* `org.gradle.experimental.declarative-common`

Properties that don’t follow the naming rules, and SHOULD be renamed:

* `org.gradle.unsafe.isolated-projects`
* `org.gradle.unsafe.suppress-gradle-api`

### Properties of incubating features

Properties of incubating features MUST start with `org.gradle.<feature-name>.`

The qualifier is absent for these properties, making the name more concise.

This property name SHOULD stay the same when the feature becomes stable.

Despite the incubation status of the feature, these properties MUST be stable.
If they need to be renamed or removed (together or separately from the feature itself), they MUST go through a deprecation cycle and the actual property change MUST happen in a major release.
Since the naming of the properties no longer suggests they are experimental, we want to ensure users are aware if the current property stops having an effect.
Since the underlying feature is incubating, its actual behavior can still change or be removed in the minor releases.

These properties CAN be supplemented with command-line options and they SHOULD follow the shared naming scheme described above.

Examples of properties in this category:

* `org.gradle.configuration-cache.parallel`

### Properties of stable features

Properties of stable features MUST start with `org.gradle.<feature-name>.`

The qualifier is absent for these properties, making the name more concise.
This is a sensible default, as the majority of features are of this nature.

These properties MUST be stable.
If they need to be renamed or removed (together or separately from the feature itself), they MUST go through a deprecation cycle and the actual change MUST happen in a major release.

These properties CAN be supplemented with command-line options and they SHOULD follow the shared naming scheme described above.

Examples of properties in this category:

* `org.gradle.caching`
* `org.gradle.configuration-cache`

## Status

PROPOSED

## Consequences

* Internal properties must start with `org.gradle.internal.`
* Properties of pre-incubation features must start with `org.gradle.experimental.`
* Properties of incubating and stable features must start with `org.gradle.<feature-name>.`

