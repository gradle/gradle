## Composite build

This feature spec introduces the concept of a ‘Gradle composite build’. A composite build is a lightweight assembly of Gradle projects that a developer is working on. These projects may come from different Gradle builds, but when assembled into a composite Gradle is able to coordinate these projects so that they appear in some way as a single build unit.

#### Composite build participants

Each separate Gradle build that provides projects for a composite build is a _build participant_. Each build participant has a version of Gradle that is used for building. A composite build is defined as _homogeneous_ if every participant is built with the same Gradle version.

#### Independent configuration models

Gradle builds within a composite will be configured independently, and will have no direct access to the configuration model of other builds within the composite. This will permit these builds to be configured lazily and in parallel.

#### Project dependency substitution

Where possible, external dependencies will be replaced with project dependencies within a composite. In this way, a composite build will perform similarly to a multi-project build containing all of the projects.

So, for example, application A and library B might normally be built separately, as part of different builds. In this instance, application A would have a binary dependency on library B, consuming it as a jar downloaded from a binary repository. When application A and library B are both imported in the same composite, however, application A would have a source dependency on library B.

#### Composite builds in the IDE

A tooling API client will be able to define a composite and query it in a similar way to how a `ProjectConnection` can be queried. While the projects contained in a composite may come from separate Gradle builds, where possible the composite will present as if it were a single, unified Gradle build containing all of the projects for each participating Gradle build.

This will provide the developer with a view of all the projects in the composite, so that the developer can search for usages or make changes to any of these projects. When the developer compiles or runs tests from withing the IDE, these changes will be picked up for any dependent project. However, IDE actions that delegate to Gradle (such as task execution) will not operate on a composite build, as these actions will not (yet) be composite-aware.

#### Composite builds from the command-line

A command-line client will be able to point to a composite build definition, and execute tasks for the composite as a whole.

## Milestones

### Milestone: Tooling client can define composite and request IDE models

After this milestone, a Tooling client can define a composite build, and request the `EclipseProject` model for each included project.

##### Stories

- [ ] [Tooling client provides model for "composite" with one multi-project participant](tooling-api-model/single-build)
- [ ] [Tooling client provides model for composite containing multiple participants](tooling-api-model/multiple-builds)
- [ ] [Tooling models for composite are produced by a single daemon instance](tooling-api-model/composed-in-daemon)

##### Open Questions

- Is `GradleConnection` or `GradleBuildConnection` a better name than `CompositeBuildConnection`?
    - This API will be useful to connect an individual Gradle build (single project or multi-project)
    - Implementation _may_ be different for a single-build connection: no need to have a separate daemon process involved

### Milestone: IDE models for composite build include dependency substitution

After this milestone, a Tooling client can define a _homogeneous_ composite build (one where all participants have same Gradle version), and the `EclipseProject` model returned will have external dependencies replaced with project dependencies.

##### Stories:

Tooling client defines a homogeneous composite and:

- [ ] IDE models have external dependencies directly replaced with dependencies on composite project publications
    - Naive implementation of dependency substitution: metadata is not considered
    - Retrieve the `ProjectPublications` instance for every `EclipseProject` in the composite
    - Adapt the returned set of `EclipseProject` instances by replacing Classpath entries with project dependencies
- [ ] IDE models are resolved with project dependencies substituted for external dependencies
    - Implement real dependency substitution for composite build (all participants must have the same Gradle version)
    - Provide a "Composite Context" (containing all project publication information) when requesting `EclipseProject` model from each build
    - Likely remove the use of Tooling API to communicate with each participant

### Milestone: Tooling client can define composite and execute tasks

After this milestone, a Tooling client can define a homogeneous composite build, and execute tasks for projects within the composite. When performing dependency resolution for tasks, external dependencies will be replaced with appropriate project dependencies.

##### Stories:

Tooling client defines a composite and:

- [ ] Executes `dependencies` task for project, demonstrating appropriate dependency substitution
- [ ] Executes task that uses artifacts from substituted dependency: assumes artifact was built previously
- [ ] Executes task that uses artifacts from substituted dependency: artifact is built on demand

### Milestone: Command-line user can execute tasks in composite build

After this milestone, a Build author can define a homogeneous composite and use the command line to executes tasks for a project within the composite. The mechanism(s) for defining a composite outside of the Tooling API have not yet been determined.

## Later

- Model/task support for heterogeneous composites (different versions of Gradle)
- Model/task support for older Gradle versions
