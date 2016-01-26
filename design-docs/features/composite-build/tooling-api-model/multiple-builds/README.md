## Tooling client provides model for composite containing multiple participants

### Overview

- Projects using < Gradle 1.8 do not support `ProjectPublications`
- Projects using < Gradle 2.5 do not support dependency substitution

### API

    class GradleCompositeException extends GradleConnectionException {
    }

### Implementation notes

- Client will provide connection information for multiple builds (root project)
- Methods will delegate to each `ProjectConnection` and aggregate results.
- Only a aggregate result will be returned (no partial results). 
- Each participant in the composite will be used sequentially
- Overall operation fails on the first failure (no subsequent participants are queried).
- Implement "composite" ModelBuilder<Set<T>> implementation.
    - `models()` returns a composite `ModelBuilder<Set<T>>`
    - When `get()` is used, each participant's `ModelBuilder<T>` is configured and called.
    - `ResultHandler<Set<T>>.onComplete()` gets aggregated result.
    - `ResultHandler<Set<T>>.onFailure()` gets the first failure
- Order of participants added to a composite does not guarantee an order when operating on participants or returning results.  A composite with [A,B,C] will return the same results as a composite with [C,B,A], but results are not guaranteed to be in any particular order.

### Test coverage

- Including 'single-build' tests, except relaxing allowed # of participants.
- When composite build connection is closed, all `ProjectConnection`s are closed.
- When retrieving an `EclipseProject` with getModels(modelType), getModels(modelType, resultHandler), models(modelType)
    - with two 'single' projects, two `EclipseProject`s are returned.
    - with two multi-project builds, a `EclipseProject` is returned for every project in both builds.
    - if any participant throws an error, the overall operation fails with a `GradleCompositeException`
- Check that a consumer can cancel an operation
- Check that all `ModelBuilder` methods are forwarded to each underlying build's `ModelBuilder` when configuring a build specific `ModelBuilder`
- Check that retrieving a model fails on the first `ProjectConnection` failure
- Fail if participant is a subproject of another participant.
- After a successful model request, on a subsequent request:
    - Making one participant a subproject of another causes the request to fail


### Documentation

- Add toolingApi sample with multiple independent builds. Demonstrate retrieving models for all projects.

### Open issues

- TBD