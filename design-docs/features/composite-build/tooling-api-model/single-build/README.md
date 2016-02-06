## Tooling client provides model for "composite" with one multi-project participant

### Overview

- This story provides an API for retrieving an aggregate model (single type from multiple projects) through the TAPI.
- With the existing `ProjectConnection` API, aggregation must be done on the client side or a model type must be a `HierarchicalElement` (only works for multi-project builds).
- It will only support retrieving models for `EclipseProject`.  Later stories add support for `ProjectPublications`, and eventually any model type should be able to be aggregated from a composite build.

### API

To support Eclipse import, only a constrained composite connection API is required.

    abstract class GradleConnector { // existing class
        static GradleConnection.Builder newGradleConnectionBuilder()
    }

    // See code in 'composite-build/src'

    // Usage:
    GradleConnection connection = GradleConnector.newGradleConnectionBuilder().
        addBuild(new File("path/to/root")).
        build()

    // Using blocking call
    Set<EclipseProject> projects = connection.getModels(EclipseProject.class)
    for (EclipseProject project : projects) {
        // do something with EclipseProject model
    }

    // Using ModelBuilder
    ModelBuilder<Set<EclipseProject>> modelBuilder = connection.models(EclipseProject.class)
    Set<EclipseProject> projects = modelBuilder.get()

    // using result handler
    // or connection.getModels(EclipseProject.class, ...)
    modelBuilder.get(new ResultHandler<Set<EclipseProject>>() {
        @Override
        public void onComplete(Set<EclipseProject> result) {
            // handle complete result set
        }

        @Override
        public void onFailure(GradleConnectionException failure) {
            // handle failures
        }
    })

### Implementation notes

- Implement `GradleConnection` on top of existing Tooling API client
- Create `ProjectConnection` instance for the participant
- Delegate calls to the participant's `ProjectConnection`
    - Optimize for `EclipseProject`: open a single connection and traverse hierarchy
    - Fail for any other model type
- Gather all `EclipseProject`s into result Set
- After closing a `GradleConnection`, `GradleConnection` methods throw IllegalStateException (like `ProjectConnection.getModel`)
- All `ModelBuilder` methods are delegates to the underlying `ProjectConnection`
- Validate participant projects are a "valid" composite before retrieving model
    - >1 participants
    - All using >= Gradle 1.0
    - Participants are not "overlapping" (subprojects of one another)
    - Participants are actually Gradle builds

### Test coverage

- Fail with `IllegalStateException` if no participants are added to the composite when connecting.
- Fail with `UnsupportedOperationException` if composite build is created with >1 participant when connecting.
- Fail with `IllegalStateException` after connecting to a `GradleConnection`, closing the connection and trying to retrieve a model.
- Errors from trying to retrieve models (getModels, et al) is propagated to caller.
- When retrieving anything other than `EclipseProject`, an `UnsupportedOperationException` is thrown.
- When retrieving `EclipseProject`:
    - a single ProjectConnection is used.
    - a single project returns a single `EclipseProject`
    - a multi-project build returns a `EclipseProject` for each Gradle project in a Set
- Fail if participant is not a Gradle build (what does this look like for existing integration tests?)
- After making a successful model request, on a subsequent model request:
    - Changing the set of sub-projects changes the number of `EclipseProject`s that are returned
    - Removing the project directory is causes a failure
    - Changing a single build into a multi-project build changes the number of `EclipseProject`s that are returned
- Errors from closing underlying ProjectConnection propagate to caller.
- Changing the participants Gradle distribution is reflected in the `ProjectConnection`
- Participant project directory is used as the project directory for the `ProjectConnection`
- Cross-version tests:
    - Fail if participants are <Gradle 1.0
    - Test retrieving `EclipseProject` from all supported Gradle versions

### Documentation

- Need to rework sample or add composite sample using new API.
- Add toolingApi sample with a single multi-project build. Demonstrate retrieving models from all projects.

### Open issues

- Provide way of detecting feature set of composite build?
- Enable validation of composite build -- better way than querying model multiple times?
