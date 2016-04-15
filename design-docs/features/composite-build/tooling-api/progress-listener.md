## Tooling client provides progress listener for composite model request

### Overview

Progress listeners should be supported for composite model requests in the
same way as it is supported for ordinary project connection model requests.
The support added in this story for progress listener for composite model 
requests.

### API

The existing `org.gradle.tooling.ModelBuilder` and `org.gradle.tooling.BuildLauncher` interfaces extend from `ConfigurableLauncher` (which extends `LongRunningOperation`).  It contains four `addProgressListener` methods.

```
    LongRunningOperation addProgressListener(org.gradle.tooling.ProgressListener listener);
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener);
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> operationTypes);
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes);
```

This story will implement support for both listener types (`org.gradle.tooling.ProgressListener` and `org.gradle.tooling.events.ProgressListener`).

### Implementation notes

- A user will configure a "composite level" `ModelBuilder` or `BuildLauncher` using the existing interfaces.
- In the coordinator, when configuring the "build level" `ModelBuilder` or `BuildLauncher` (both `AbstractLongRunningOperation`), a new internal method will take a given "composite level" `ConsumerOperationParameters` and copy the different kinds of listeners to the build level (via `ConsumerOperationParameters.Builder`).

### Test coverage

- Compare progress events created by retrieving a model directly from a ProjectConnection and with the composite's GradleConnection API
  - the events produced by the composite request should contain all events that are produced by a direct ProjectConnection
    - Compare executing tasks and retrieving models
    - it is acceptable that the composite request produces some extra events
  - compare with a single build
  - compare with 3 builds in a composite
