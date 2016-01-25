## Tooling models for composite are produced by a single daemon instance

### Overview

The composition of builds should be implemented by a single daemon instance.
This helps preparing composite builds for command line usage when build composition
is implemented in the daemon process.

### API

This story doesn't change the API of the composite build. This story is limited to implementing the  `<T> Set<ModelResult<T>> getModels(Class<T> modelType)` method of the `CompositeBuildConnection` interface.
Other model related methods of the designed `CompositeBuildConnection` interface are implemented in later stories.

### Implementation notes

The implementation involves a client part ("consumer") and a server part ("provider").
On the consumer side, the idea is to extend the current Tooling API client and reuse the infrastructure used for `ProjectConnection` and share most of it for `CompositeBuildConnection`.
Optimally this means the the underlying `ConsumerConnection` infrastructure could be reused and shared.
The `getModels` API method uses a different pattern that is currently used in Tooling API client. Supporting this requires changes to the consumer side infrastructure and possibly also in the serialization protocol used in the daemon/provider connection.

Later stories will require the possibility to use parameters for model requests. An example of such model is `<T> T getModel(ProjectIdentity id, Class<T> modelType)`. This story doesn't require that behaviour, but it might be beneficial to start preparing for that change in the implementation.

The provider side and daemon execution implementation is in the launcher module.
The execution of composite build models need composite build specific `BuildAction` classes that are executed by composite build specific `BuildActionRunner` implementation classes.
This story needs `CompositeBuildModelAction` and `CompositeBuildModelActionRunner` classes that should reside in the `laucher` module.
That module already depends on tooling-api and core modules. Therefore using the tooling api for accessing participant projects should be possible.

### Test coverage

Existing test coverage for `<T> Set<ModelResult<T>> getModels(Class<T> modelType)` API method should pass. There are existing tests for the previous stories where this behaviour has been implemented on the client side.

### Documentation

### Open issues
- Provide way to specify composite build Gradle distribution
