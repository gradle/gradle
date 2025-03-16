# ADR-0004 - Use a platform-oriented architecture for Gradle

## Date

2024-02-07

## Context

The Gradle code base is essentially a large monolith, without strong internal boundaries.
This has a number of negative effects on productivity, including:

- Unclear ownership of code.
- Difficult to focus on one particular area.
- Unintended coupling between areas of the code, including tests.

## Decision

Organize the Gradle code base into a set of coarse-grained "architecture modules".
An architecture module is responsible for providing a coherent set of features and:

- Provides a set of APIs and services for use from outside the module.
- Has a private implementation.
- Is owned by a single team. A team may own multiple architecture modules.

The modules are arranged into several different "Gradle platforms".
A Gradle platform is a logical distribution that provides support for a specific kind of automation.
A typical platform builds on other platforms in order to add more capabilities, for example, to add support for a particular language.

See the [discovery document](https://docs.google.com/document/d/1-oKG23gLdx2D2uJvzir31AhDFyqSf81LDESfKKCU28c/edit#heading=h.pps74pn68uvk) (internal document) for more context.

### Platforms

The platforms and their architecture modules are:

#### Core automation platform

This is a general-purpose automation platform which takes care of the efficient definition and execution of work, such as tasks.
This platform is agnostic to what exactly the purpose of the work is.
It might be creating an application, setting up development environments, orchestrating deployments, running simulations, etc.

This platform does not provide special support for a particular kind of automation. This is the responsibility of other platforms. 

It is made up of 3 architecture modules:

- **Runtime**: Provides the runtimes or "containers" in which code runs. These runtimes include the Gradle client, the daemon and the worker processes. This is the base module on which all other architecture modules depend.
- **Configuration**: Allows the build structure and work, such as tasks, to be specified. This includes the project model, the DSL and so on.
- **Execution**: Runs the work efficiently. This includes scheduling, execution, caching and so on.

#### Software development platform

This is a general purpose platform that builds on the core automation platform to add support for the automation of software development.
This includes work such as compiling, testing and documenting software, plus sharing that software via publishing and dependency management.
This platform is agnostic to what kind of software is being developed.
It might be Java or Kotlin libraries running on the JVM, Gradle plugins, Android or iOS applications, C++ libraries, and so on.

This platform does not provide special support for a particular language or ecosystem.

#### JVM platform

This is a platform that builds on the core and software platforms to add support for developing software that runs on the JVM.
This includes software that is implemented using Java, Kotlin or some other JVM language.

This platform provides specific support for Java, Groovy and Scala, and includes the foojay toolchain plugin. 

#### Extensibility platform

This is a platform that builds on the core, software and JVM platforms to add support for extending Gradle by implementing and applying plugins.

This platform includes the plugin publishing plugin and the plugin portal.

#### Native platform

This is a platform that builds on the core and software platforms to add support for developing native software.

This platform provides specific support for Swift, C++ and C.

### Cross-cutting architecture modules

There are some additional cross-cutting architecture modules that aren't themselves platforms:

#### Enterprise integration

Provides cross-cutting integration with Gradle's commercial product.

#### IDE integration

Provides cross-cutting integration with IDEs and other tooling.

#### Build infrastructure

Provides build logic, libraries, test suites and infrastructure to support developing and releasing Gradle.

#### Documentation

Provides cross-cutting Gradle documentation and samples, along with the infrastructure to write, test, publish and host the documentation.

## Status

ACCEPTED

## Consequences

- Assign ownership of each architecture module to one team.
- Assign each source file to one architecture module.
- Align the source tree layout with this architecture.
- Define and enforce the private implementation of each module.
