## Tooling models for composite are produced by a single daemon instance

### Overview

The composition of builds should be implemented by a single daemon instance.
This helps preparing composite builds for command line usage when build composition
is implemented in the daemon process.

### API

This story doesn't change the API of the composite build from previous stories. This story moves the implementation from the previous story [Tooling client provides model for composite containing multiple participants](../multiple-builds#tooling-client-provides-model-for-composite-containing-multiple-participants) into a `BuildActionExecuter` implementation which resides in the daemon process. 
Internally a `SetOfEclipseProjects` type will be used to workaround the lack of support for collection types in existing infrastructure. 
It can be assumed that the only supported model type is `EclipseProject`.

### Implementation notes

On the client side, the idea is to extend the current Tooling API client and reuse the `ConsumerConnection` infrastructure.
The implementation involves the Tooling API client "consumer" and "provider" parts besides the daemon part. The provider and daemon execution code is in the launcher module.

The execution of composite build models needs a composite build specific `BuildActionExecuter`.
The contextual information of the composite build, like participant builds, should be passed in `ConsumerOperationParameters`/`ProviderOperationParameters` parameter. These parameters should be mapped to a `BuildActionParameters` implementation that contains the composite build related contextual information in an instance of a `CompositeParameters` type. When the composite parameters are available, the `BuildActionExecuter` implementation should be chosen accordingly.

That launcher module already depends on tooling-api and core modules. Therefore using the tooling api for accessing participant projects should be possible.

### Test coverage

Existing test coverage added in the previous composite build stories should pass. 

### Documentation

### Open issues
- Provide way to specify composite build Gradle distribution
- Plan unit test coverage required for possible Tooling API consumer infrastructure changes
