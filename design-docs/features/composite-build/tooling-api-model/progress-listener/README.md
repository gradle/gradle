## Tooling client provides progress listener for composite model request

### Overview

Progress listeners should be supported for composite model requests in the
same way as it is supported for ordinary project connection model requests.
The support added in this story for progress listener for composite model 
requests will be limited to providing the progress listener interface
that Buildship is currently using.

### API

The existing `org.gradle.tooling.ModelBuilder` interface extends from `ConfigurableLauncher` interface (which extends `LongRunningOperation`)  which contains four `addProgressListener` methods.

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



### Test coverage

### Documentation

### Open issues
