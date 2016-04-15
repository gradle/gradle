## Tooling Client specifies builds that make up a composite

### Overview

- This story provides an API for defining a `GradleConnection`, which represents a single connection to a set of Gradle builds.
- Later stories use this API for retrieving tooling models and executing tasks.

### API

    GradleConnectionBuilder builder = GradleConnector.newGradleConnection();

    builder.useGradleUserHomeDir(gradleUserHome);
    builder.newParticipant(projectDir).useInstallation(gradleHome).create()

    GradleConnection connection = builder.build();

### Implementation notes

- Implementation `GradleConnection` on top of existing Tooling API client
- Create `ProjectConnection` instance for the participant
- Delegate calls to the participant's `ProjectConnection`
    - Optimize for `EclipseProject`: open a single connection and traverse hierarchy
    - Fail for any other model type
- Gather all `EclipseProject`s into result Set
- After closing a `GradleConnection`, `GradleConnection` methods throw IllegalStateException (like `ProjectConnection.getModel`)
- `CompositeModelBuilder` is an extension of `ModelBuilder`, allowing to set per-participant arguments on top of arguments for the coordinator.
- All `CompositeModelBuilder` methods are delegates to the underlying `ProjectConnection`
- Order of participants added to a composite does not guarantee an order when operating on participants or returning results.  A composite with [A,B,C] will return the same results as a composite with [C,B,A], but results are not guaranteed to be in any particular order.

### Test coverage

- Fail with `IllegalStateException` if no participants are added to the composite when connecting.
- Fail if participant is not a Gradle build (what does this look like for existing integration tests?)
- Fail if participant is a subproject of another participant.
- Cross-version tests:
    - Fail if participants are <Gradle 1.0

### Documentation

- Add toolingApi sample that creates a `GradleConnection` with multiple independent builds.
