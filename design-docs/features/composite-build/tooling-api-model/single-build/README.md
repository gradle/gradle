## Tooling client provides model for "composite" with one multi-project participant

### Overview

- This story provides an API for retrieving an aggregate model (single type from multiple projects) through the TAPI.
- With the existing `ProjectConnection` API, aggregation must be done on the client side or a model type must be a `HierarchicalElement` (only works for multi-project builds).
- It will only support retrieving models for `EclipseProject`.  Later stories add support for `ProjectPublications`, and eventually any model type should be able to be aggregated from a composite build.
- TAPI clients must use >= Gradle 2.12. Participant projects can be mixed, but not all features of a composite build may be supported.

### API

To support Eclipse import, only a constrained composite connection API is required.
    GradleConnector { // existing class
        static GradleConnection.Builder newGradleConnection()
    }

    interface GradleBuild extends GradleDistributionAware {
    }

    interface GradleDistributionAware {
        void useInstallation(File gradleHome);
        void useGradleVersion(String gradleVersion);
        void useDistribution(URI location);
    }

    interface GradleConnection {    
        interface Builder { 
            GradleBuild addBuild(File rootProjectDirectory);
            GradleConnection build() throws GradleConnectionException;
        }

        <T> Set<T> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException
        void close()
    }

    // Usage:
    GradleConnection.Builder builder = GradleConnector.newGradleConnection()
    GradleBuild projectBuild1 = builder.addBuild(new File("path/to/project-build-1"))
    projectBuild1.useGradleVersion(new File("path/to/gradles"))
    GradleConnection connection = builder.build()

    Set<EclipseProject> projects = connection.getModels(EclipseProject.class)
    for (EclipseProject project : projects) {
        // do something with EclipseProject model
    }

### Implementation notes

- Implement `GradleConnection` on top of existing Tooling API client
- Create `ProjectConnection` instance for the participant
- Delegating all `getModels()` calls to the `ProjectConnection`
    - Optimize for `EclipseProject`: open a single connection and traverse hierarchy
    - Fail for any other model type
- Gather all `EclipseProject` into result Set
- After closing a `GradleConnection`, `getModels` throws IllegalStateException (like `ProjectConnection.getModel`)

### Test coverage

- Fail with `IllegalStateException` if no participants are added to the composite when connecting.
- Fail with `UnsupportedOperationException` if composite build is created with >1 participant when connecting.
- Fail with `UnsupportedVersionException` if composite build participant is using < Gradle 2.12 when retrieving model.
- Fail with `IllegalStateException` after connecting to a `GradleConnection`, closing the connection and trying to retrieve a model.
- Errors from trying to retrieve models (getModels) is propagated to caller.
- Errors from closing underlying ProjectConnection propagate to caller.
- When retrieving anything other than `EclipseProject`, an `UnsupportedOperationException` is thrown.
- When retrieving `EclipseProject`, 
    - a single ProjectConnection is used.
    - a single project returns a single `EclipseProject`
    - a multi-project build returns a `EclipseProject` for each project in a flatten set (does not rely on hierarchy)
- Changing the participants Gradle distribution is reflected in the `ProjectConnection`
- Participant project directory is used as the project directory for the `ProjectConnection`


### Documentation

- Need to rework sample or add parallel sample using new API.

### Open issues

- Should we do any pre-checking before connecting
    - Check that rootProjectDir exists?
    - Check that it's a root project?
- Provide way of detecting feature set of composite build?
