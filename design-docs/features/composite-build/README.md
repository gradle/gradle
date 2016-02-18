## Composite build

This feature spec introduces the concept of a ‘Gradle composite build’. A composite build is a lightweight assembly of Gradle projects that a developer is working on. These projects may come from different Gradle builds, but when assembled into a composite Gradle is able to coordinate these projects so that they appear in some way as a single build unit.

#### Composite build coordinator

The overall execution of tasks and models for a composite build is the responsibility of the _build coordinator_. The coordinator is responsible for wiring together the different builds within the composite.

#### Composite build participants

Each separate Gradle build that provides projects for a composite build is a _build participant_. Each build participant has a version of Gradle that is used for building.

A composite is considered _integrated_ if all of the participant Gradle versions are fully supported for composite build. This allows the coordinator to enable features like project dependency substitution for models and tasks within the composite.

A composite is considered _aggregated_ if the participant Gradle versions are not fully supported for composite build. This allows the coordinator to still provide features like model retrieval and task execution, but each participant build is completely independent.

In the initial implementation, a composite will only be _integrated_ if all build participants use the same, latest version of Gradle. Further work add support for integrated composites with certain older versions of Gradle.

#### Independent configuration models

Gradle builds within a composite will be configured independently, and will have no direct access to the configuration model of other builds within the composite. This will permit these builds to be configured lazily and in parallel.

#### Project dependency substitution

For an _integrated_ composite, external dependencies will be replaced with project dependencies when constructing models and executing tasks. In this way, an integrated composite build will perform similarly to a multi-project build containing all of the projects.

When performing dependency substitution in a composite, one participant build is the _consumer_ with one or more other participants acting in the _producer_ role. When the consumer resolves an external dependency that matches the publication of one of the producers, then the external dependency is replaced with a dependency on the producer project.

Consider independent Gradle builds A and B that are combined into an integrated composite. When resolving a model or executing a task in A, then A would be the _consumer_ and B would be a _producer_. If A consumes a dependency on "org:B:1.0" and B produces the publication "org:B:" then the external dependency will be replaced with a project dependency on B.

#### Composite builds in the IDE

A tooling API client will be able to define a composite and query it in a similar way to how a `ProjectConnection` can be queried. While the projects contained in a composite may come from separate Gradle builds, where possible the composite will present as if it were a single, unified Gradle build containing all of the projects for each participating Gradle build.

This will provide the developer with a view of all the projects in the composite, so that the developer can search for usages or make changes to any of these projects. When the developer compiles or runs tests from withing the IDE, these changes will be picked up for any dependent project. Furthermore, IDE actions that delegate to Gradle (such as task execution) will operate on a composite build, such that dependency resolution will utilize project dependency substitution.

TAPI clients must use the latest version of Gradle to use composite builds. Participant projects can be mixed, but not all features of a composite build may be supported.

#### Composite builds from the command-line

A command-line client will be able to point to a composite build definition, and execute tasks for the composite as a whole.

## Target use-case

- All projects use the same, latest version of Gradle
- Java projects, using either `uploadArchives` or the publishing plugins
- Maximum 10 Gradle projects involved, split into <= 10 participant build
- May take up to twice as much time for task execution as a 10 project multi-project build: goal is to take less time for execution

## Milestones

### Milestone: Tooling client can define a composite build and request tooling models

This milestone adds a new Tooling client API that can define a composite build, and request the `EclipseProject` or other tooling model for each included project. First class IDE integration support for composite build is a high priority and starting with a Tooling API interface to composite build has a number of advantages:
 - Ensure that composite build is designed and developed in a way that will support IDE import of a composite
 - Allow for multiple schemes for declaring the set of participants in a composite
 - Ensure that non-homogeneous composites are considered early on

##### Overview

On completion of the milestone, a user will be able to use the demo for composite build to:

- Use a `GradleConnection` to define a set of builds to participate in a composite
- View error messages when the composite participants are not valid (e.g. not root projects)
- Retrieve and view the aggregated set of models for all projects within the composite

##### Stories

- [ ] [Tooling client provides `EclipseProject` model for "composite" with one multi-project participant](tooling-api-model/single-build)
- [ ] [Tooling client provides `EclipseProject` model for composite containing multiple participants](tooling-api-model/multiple-builds)
- [ ] [Tooling models for composite are produced by a single daemon instance](tooling-api-model/composed-in-daemon)
- [ ] Tooling client requests `IdeaModule` model for every project in a composite
- [ ] Tooling client requests `BuildEnvironment` model for every project in a composite
- [ ] Tooling client requests arbitrary model type for every project in a composite

### Milestone: Buildship (or IDEA) uses Tooling client to define composite

In order to be fully usable integrated into a real IDE, several additional features will need to be implemented on the existing tooling client for composite builds. These features include: cancellation of model requests, progress events for model requests, specifying per-participant build parameters (project args, JVM args, etc), and others.

On completion of this milestone, the Buildship plugin for Eclipse will be modified so that it uses the Gradle Tooling API to define a composite for all Gradle builds that are imported into the workspace. Buildship will use a `GradleConnection` to define the composite and to retrieve models in any associated Gradle builds.

For integrated composites, project dependency substitution will automatically be enabled for Buildship through use of this feature.

##### Stories

- [ ] [Tooling client cancels composite model request](tooling-api-model/cancellation)
- [ ] [Tooling client provides progress listener for composite model request](tooling-api-model/progress-listener)
- [ ] Tooling client specifies stdout and stderr for composite model request
- [ ] Tooling client provides separate project parameters and JVM args for each participant in composite model request

It is possible that further work will be required to allow to fully integrate Buildship with the Gradle Tooling API.

### Milestone: IDE models for a composite include dependency substitution

When a Tooling client defines an _integrated_ composite build, the `EclipseProject` model (and other IDE models) should have appropriate external dependencies replaced with project dependencies.

In this initial milestone, an integrated composite will be one where the coordinator and all participants are configured to build with the same, latest version of Gradle . This will be the case where:
- The Tooling Client is using the latest version of Gradle to declare the composite
- Each participant is either not configured to use the Gradle wrapper or is configured to use the wrapper with the latest version of Gradle

Which projects are substituted will be dependent on the publication model for each participant project. The publication model can be derived from various sources, including the `uploadArchives` task and `publishing.publications`.

The metadata for each project publication will be derived via a simple mechanism from the declared `Configuration` instances, meaning that a participant project dependency in a composite build will be treated in the same way as a regular project dependency in a multiproject build.

When substituting, the version of the project publication will not be considered.

On completion of this milestone, a user will be able to use the demo client for composite build to:

- Define an integrated composite consisting of a build participant A and a number of other single-project and multi-project build participants
- Using build participant A as a _consumer_, declare dependencies on external publications from other build participants
- View the `EclipseProject` model for build participant A, noting that the external dependencies have been substituted by project dependencies
    - Transitive dependencies resolved for build A will also be substituted
    - Dependency metadata (dependencies and excludes) for substituted project will be honoured

##### Stories:

Tooling client defines a composite and:

- [ ] [IDE model has external dependency substituted with project dependency with no transitive dependencies](dependency-substitution/direct-replacement)
- [ ] [IDE model has external dependency substituted with project dependency](dependency-substitution/resolved-substitution)

### Milestone: Tooling client can define a composite and execute tasks

After this milestone, a Tooling client can define a composite build, and execute tasks for projects within the composite. Where possible, external dependencies will be replaced with appropriate project dependencies when performing dependency resolution for tasks.

##### Stories:

Tooling client defines a composite and:

- [ ] Executes task in a single project within a heterogeneous composite
    - Need API that provides a `BuildLauncher` for a particular project
- [ ] Executes `dependencies` task for a single project, demonstrating appropriate dependency substitution
    - This is likely to "just work", and require some basic test coverage
- [ ] Executes task that uses artifacts from substituted dependency: assumes artifact was built previously
    - This wires in the artifact files from the producer projects, but does not wire in the task dependency
- [ ] Executes task that uses artifacts from substituted dependency: artifact is built on demand
    - This wires in the task dependency to produce the actual artifact

##### Out of scope

- Execute all tasks matching name across all projects in composite
- Handling command-line arguments that might not be appropriate for composite build (e.g. --project-dir)

### Milestone: Command-line user can execute tasks in composite build

After this milestone, a Build author can define a homogeneous composite and use the command line to executes tasks for a project within the composite. The mechanism(s) for defining a composite outside of the Tooling API have not yet been determined.

- [ ] Developer declares composite participants using composite descriptor
    - New command-line switch to indicate composite build (or possibly the presense of an empty composite descriptor)
    - Gradle lists all of the participants
- [ ] Developer executes tasks in a single project within defined composite
    - Command-line switch to target a particular project within the composite (likely the same switch)
    - Prevent certain inappropriate arguments

##### Out of scope

- Execution of tasks across all projects in composite

### Milestone: Projects within a composite are configured at most once per task execution

The initial implementation of composite build will require every project in the composite to be configured once, with the target consumer project and any producer projects involved being configured twice. The performance impact of this will be unacceptable, and will be addressed through some additional features in composite build.

##### Stories:

- Retrieve models for all projects in a multi-project build in a single request
    - Don't configure non-root projects to construct the publication model or other requested models
- When a project is configured to construct the publication model, reuse that configured project to construct other models
    - Every root project in the composite will be configured _once_ for a model request
- When a project is configured to construct the publication model, reuse that configured project for task execution
    - Every root project in the composite will be configured _once_ for task execution
- Determine publication coordinates for each project without fully configuration (possibly via caching)
    - Projects that are not producers or consumers in a particular execution are not configured
- Use coordinator process for one or more participant builds

##### Out of scope

- Optimize number and lifecycle of daemons generated for composite build

### Milestone: Integrated composite build can contain participants using older versions of Gradle

This milestone will add support for an _integrated_ composite consisting of participants that build with different versions of Gradle, some of which are not the latest version. The exact versions that will be supported is to be determined.

The Gradle version for a participant build can be declared either:
- Via the Tooling API when the composite is defined
- Via a configured wrapper, in the `gradle-wrapper.properties` file

On completion of this milestone, a composite build will be _integrated_ as long as all participants are configured to build with a Gradle version that is supported for composite build, and the coordinator is executed with the latest version of Gradle.

### Milestone: Play application projects are integrated into a composite build

The `play-application` plugin is built on the experimental software model, but uses regular `Configuration` instances for dependency resolution. It's possible that a composite will "just work" when a Play project is included, with Play projects behaving as fully-fledged composite build participants. However, further investigation and additional test coverage will be required.

### Milestone: JVM Software Model components are integrated into a composite build

The experimental JVM software model does not use regular `Configuration` instances for dependency resolution. As such, there is likely to be additional work required so that JVM software model projects can behave as fully-fledged composite build participants.

### Milestone: Tooling API executes tests in a composite build

## Later

- Will need to report 'capabilities' of composite back to Tooling client (and command line client)
- Use composite build to model `buildSrc` project
- Support for composite builds in Gradle TestKit

