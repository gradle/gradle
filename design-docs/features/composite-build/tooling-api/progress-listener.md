## Tooling client provides progress listener for composite model request

### Overview

Progress listeners should be supported for composite model requests in the
same way as it is supported for ordinary project connection model requests.
The support added in this story for progress listener for composite model 
requests will be limited to providing the progress listener interface
that Buildship is currently using for model requests.

### API

The existing `org.gradle.tooling.ModelBuilder` interface extends from `ConfigurableLauncher` interface (which extends `LongRunningOperation`).  It contains four `addProgressListener` methods.

```
    LongRunningOperation addProgressListener(org.gradle.tooling.ProgressListener listener);
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener);
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> operationTypes);
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes);
```
This story will implement support for the method accepting the `org.gradle.tooling.ProgressListener` instance. That [is used in Buildship](https://github.com/eclipse/buildship/blob/3e17226/org.eclipse.buildship.core/src/main/java/org/eclipse/buildship/core/gradle/LoadEclipseGradleBuildJob.java#L85).
```
LongRunningOperation addProgressListener(org.gradle.tooling.ProgressListener listener);
```
Support for the newer "typed" `org.gradle.tooling.events.ProgressListener` interface will be added by some future story.

### Implementation notes

The `org.gradle.tooling.ProgressListener` is the so called original logging-derived progress listener. These events are currently created by using the `ProgressLogger` API. The plan is to reconstruct the `ProgressLogger` calls from events received on a `org.gradle.tooling.ProgressListener` listener. 

The progress events from the participant builds are received on `ProgressListener` interfaces. It contains only a single method `statusChanged` which accepts a `ProgressEvent` that only has a single method `getDescription`.
Creating events with `ProgressLogger` requires separate calls to `start` and `completed`. 

The current `org.gradle.tooling.internal.consumer.parameters.ProgressListenerAdapter` implementation shows the current way should ProgressListener events are created. 
When the operation ends, a statusChange event is created for the previous operation on the call stack. The last operation ends with an empty string. 
Using this assumption, it is possible to reconstruct the events assuming that two consecutive event descriptions are unique.

### Test coverage

- compare progress events created by retrieving a model directly from a ProjectConnection and with the composite's GradleConnection API
  - the events produced by the composite request should contain all events that are produced by a direct ProjectConnection
    - it is acceptable that the composite request produces some extra events
  - compare with a single build
  - compare with 3 builds in a composite

