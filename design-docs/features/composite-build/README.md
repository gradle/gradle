## Composite build

At this stage, this is a scratchpad of ideas for how "composite build" might be developed in Gradle. Later this feature and the related stories will be more fully fleshed out.

## Milestone: Tooling client defines composite and requests `EclipseProject` model for each included project

- [ ] Tooling client declares "composite" with one multi-project participant and requests tooling model for each included project
    - Implement `CompositeBuildConnection` on top of existing Tooling API client
    - Create `ProjectConnection` instance for the participant
    - Delegating all `getModels()` calls to the `ProjectConnection`
        - Optimize for `EclipseProject`: open a single connection and traverse hierarchy
        - Fail for any other model type
    - Create a `ModelResult` for each `EclipseProject` instance
- [ ] Tooling client declares composite with multiple builds, and requests tooling model for each included project
    - Client will provide connection information for multiple builds (root project)
    - Delegate `getModels()` calls to each `ProjectConnection` and aggregate results
- [ ] Tooling client declares composite including multiple builds, and tooling models are produced by a single daemon instance
    - New remote Tooling API protocol, permitting the creation of each `ProjectConnection` in a daemon instance

## Milestone: Tooling client defines homogeneous composite and IDE models have external dependencies replaced with project dependencies

Tooling client defines a homogeneous composite (all participants have same Gradle version) and:

- [ ] IDE models have external dependencies directly replaced with dependencies on composite project publications
    - Naive implementation of dependency substitution: metadata is not considered
    - Retrieve the `ProjectPublications` instance for every `EclipseProject` in the composite
    - Adapt the returned set of `EclipseProject` instances by replacing Classpath entries with project dependencies
- [ ] IDE models are resolved with project dependencies substituted for external dependencies
    - Implement real dependency substitution for composite build (all participants must have the same Gradle version)
    - Provide a "Composite Context" (containing all project publication information) when requesting `EclipseProject` model from each build
    - Likely remove the use of Tooling API to communicate with each participant

## Milestone: Tooling client defines homogeneous composite and executes tasks where external dependencies are replaced with project dependencies

Tooling client defines a composite and:

- [ ] Executes `dependencies` task for project, demonstrating appropriate dependency substitution
- [ ] Executes task that uses artifacts from substituted dependency: assumes artifact was built previously
- [ ] Executes task that uses artifacts from substituted dependency: artifact is built on demand

## Milestone: Build author defines a homogeneous composite and uses command line to executes tasks for a project within the composite

## Later

- Model/task support for heterogeneous composites (different versions of Gradle)
- Model/task support for older Gradle versions


## Questions

- Is `GradleConnection` or `GradleBuildConnection` a better name than `CompositeBuildConnection`?
    - This API will be useful to connect an individual Gradle build (single project or multi-project)
    - Implementation _may_ be different for a single-build connection: no need to have a separate daemon process involved
