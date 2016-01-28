## Composite build

This feature spec introduces the concept of a ‘Gradle composite build’. A composite build is a lightweight assembly of Gradle projects that a developer is working on. These projects may come from different Gradle builds, but when assembled into a composite Gradle is able to coordinate these projects so that they appear in some way as a single build unit.

#### Composite build participants

Each separate Gradle build that provides projects for a composite build is a _build participant_. Each build participant has a version of Gradle that is used for building. A composite build is defined as _homogeneous_ if every participant is built with the same Gradle version.

TAPI clients must use >= Gradle 2.12 to use composite builds. Participant projects can be mixed, but not all features of a composite build may be supported.

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

##### Core stories

- [ ] [Tooling client provides model for "composite" with one multi-project participant](tooling-api-model/single-build)
- [ ] [Tooling client provides model for composite containing multiple participants](tooling-api-model/multiple-builds)
- [ ] [Tooling models for composite are produced by a single daemon instance](tooling-api-model/composed-in-daemon)

##### Further stories

- [ ] Tooling client cancels composite model request
- [ ] Tooling client provides progress listener for composite model request
- [ ] Tooling client specifies stdout and stderr for composite model request
- [ ] Tooling client provides common args, JVM args and stdin for in composite model request
- [ ] Tooling client provides separate args, JVM args and stdin for each participant in composite model request

##### Open questions

### Milestone: IDE models for homogeneous composite include dependency substitution

After this milestone, when a Tooling client defines a _homogeneous_ composite build (one where all participants use the same, recent version of Gradle),  the `EclipseProject` model returned will have external dependencies replaced with project dependencies.

##### Stories:

Tooling client defines a homogeneous composite and:

- [ ] [IDE models have external dependencies directly replaced with project dependencies](dependency-substitution/direct-replacement)
- [ ] [IDE models are resolved with project dependencies substituted for external dependencies](dependency-substitution/resolved-substitution)

##### Open questions

- Gracefully degrade for heterogeneous composites:
    - Same Gradle version, different JVM args or java versions
    - Different Gradle versions >= 2.12
    - Gradle versions < 2.12 (Do we do naive substitution, or no substitution?)
- Will need to report 'capabilities' of composite back to client

### Milestone: Tooling client can define composite and execute tasks

After this milestone, a Tooling client can define a composite build, and execute tasks for projects within the composite. Where possible, external dependencies will be replaced with appropriate project dependencies when performing dependency resolution for tasks.

##### Stories:

Tooling client defines a composite and:

- [ ] Executes task in a single project within a heterogeneous composite
    - Need API that provides a `BuildLauncher` for a particular project
- [ ] Executes `dependencies` task for a single project, demonstrating appropriate dependency substitution
- [ ] Executes task that uses artifacts from substituted dependency: assumes artifact was built previously
- [ ] Executes task that uses artifacts from substituted dependency: artifact is built on demand

##### Further stories

- [ ] Execute all tasks with name across all projects in composite

### Milestone: Command-line user can execute tasks in composite build

After this milestone, a Build author can define a homogeneous composite and use the command line to executes tasks for a project within the composite. The mechanism(s) for defining a composite outside of the Tooling API have not yet been determined.

- [ ] Developer executes tasks in a single project with implicit composite defined by directory
    - New command-line switch to indicate composite build (or possibly the presense of an empty composite descriptor)
    - Command-line switch to target a particular project within the composite (likely the same switch)
    - All directories in the current directory are considered to be builds participating in a composite
- [ ] Developer declares composite participants using composite descriptor


## Later

- Model/task support for heterogeneous composites (different versions of Gradle)
- Model/task support for older Gradle versions
- Support for composite builds in Gradle TestKit

