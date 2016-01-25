## Tooling models for composite are produced by a single daemon instance

### Overview

The composition of builds should be implemented by a single daemon instance.
This helps preparing composite builds for command line usage when build composition
is implemented in the daemon process.

### API

This story doesn't change the API of the composite build. This story is limited to implementing the  `<T> Set<ModelResult<T>> getModels(Class<T> modelType)` method of the `CompositeBuildConnection` interface.
Other model related methods of the designed `CompositeBuildConnection` interface are implemented in later stories.

### Implementation notes

The implementation involves a client part and a server part.
On the client part, the idea is to extend the current Tooling API client and reuse the infrastructure used for `ProjectConnection` and share most of it for `CompositeBuildConnection`.
Optimally this means the the underlying `ConsumerConnection` infrastructure could be reused and shared.
The `getModels` API method uses a different pattern that is currently used in Tooling API client. Supporting this requires changes to the client side infrastructure and possibly also in the serialization protocol used in the daemon connection.

The story doesn't use parameters for model requests so that doesn't have to be solved as part of this story. It might be worth preparing for changes in the infrastructure for methods like `<T> T getModel(ProjectIdentity id, Class<T> modelType)`.

The daemon execution part is in the launcher module. That module already depends on tooling-api and core modules. The execution of composite build models need composite build specific `BuildAction` classes that are executed by composite build specific `BuildActionRunner` implementation classes.
This story needs `CompositeBuildModelAction` and `CompositeBuildModelActionRunner` classes that should reside in the `laucher` module.

### Test coverage

Existing test coverage for `<T> Set<ModelResult<T>> getModels(Class<T> modelType)` API method should pass. There are existing tests for the previous stories where this behaviour has been implemented on the client side.

### Documentation

### Open issues
- Provide way to specify composite build Gradle distribution