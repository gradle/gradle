## Tooling client provides model for composite containing multiple participants

### Overview

- TAPI clients must use >= Gradle 2.12. Participant projects can be mixed, but not all features of a composite build may be supported.
- Projects using < Gradle 1.8 do not support `ProjectPublications`
- Projects using < Gradle 2.5 do not support dependency substitution
- Initial stories enforce a homogenous Gradle version.

### API

    class GradleCompositeException extends GradleConnectionException {}

### Implementation notes

- Client will provide connection information for multiple builds (root project)
- Delegate `getModels()` calls to each `ProjectConnection` and aggregate results

### Test coverage

- Including 'single-build' tests, except relaxing allowed # of participants.
- Fails with `UnsupportedVersionException` when any participant in the composite is <Gradle 2.12 when retrieving the first model
- When composite build connection is closed, all `ProjectConnection`s are closed.
- When retrieving an `EclipseProject`,
    - with two 'single' projects, two `EclipseProject`s are returned.
    - with two multi-project builds, a `EclipseProject` is returned for every project in both builds.
    - if any participant throws an error, the overall operation fails with a `GradleCompositeException`

### Documentation

### Open issues
- Should we immediately try to connect to all projects in a composite when connecting to the composite?
    - Check that any features will work?
    - Check that all participants are actually Gradle projects?
    - Are there any overlapping projects?