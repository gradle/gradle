### Composite build

At this stage, this is a scratchpad of ideas for how "composite build" might be developed in Gradle. Later this feature will be better described, and the following ideas could be translated into actual stories to be implemented.

### Story ideas

- Implement `CompositeBuildConnection` on top of existing Tooling API infrastructure : new client-side mechanism only
    - Adding `CompositeBuildConnector`
    - Creating `ProjectConnection` instances for each participant
    - Delegating all `getModels()` calls to each `ProjectConnection` : only can cope with `EclipseProject`
        - Fail for any request other than `EclipseProject`
    - Flatten set of `EclipseProject` instances and create a `ProjectIdentity` for each
    - Real code in the Tooling API in Gradle

- Implement naive dependency substitution in client-only `CompositeBuildConnection`
    - Get the ProjectPublications instances for every `EclipseProject` in the composite
        - Create a `ProjectConnection` for each subproject, and ask for the model
    - Adapt the returned set of `EclipseProject` instances by replacing Classpath entries with project dependencies

- Implement `CompositeBuildConnection` where the aggregation occurs in a daemon process
    - Remoting the existing client-side `CompositeBuildConnection` implementation
    - Move the client-side implementation into the daemon request handler

- Implement real dependency substitution for composite build with a single Gradle version
    - Removing the use of ToolingAPI to communicate with each participant
    - Provide the "Composite Context" (containing all project publication information) when requesting `EclipseProject` model from each build

- Implement real dependency substitution for composite build with a different Gradle versions
    - Would need to be able to supply the "Composite context" to a remote build invocation (via TAPI?)

- Implement `EclipseProject` deduplication

### Questions

- Is `GradleConnection` or `GradleBuildConnection` a better name than `CompositeBuildConnection`?
    - This API will be useful to connect an individual Gradle build (single project or multi-project)
    - Implementation _may_ be different for a single-build connection: no need to have a separate daemon process involved
