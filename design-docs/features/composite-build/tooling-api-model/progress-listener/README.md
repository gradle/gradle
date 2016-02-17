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

The `org.gradle.tooling.ProgressListener` is the so called original logging-derived progress listener. These events are currently created by using the `ProgressLogger` API. The plan is to reconstruct the `ProgressLogger` calls from events received on a `org.gradle.tooling.ProgressListener` listener. There are some open issues with this implementation approach.

### Test coverage

- compare progress events created by retrieving a model directly from a ProjectConnection and with the composite's GradleConnection API
  - events should be the same
  - compare with a single build
  - compare with 3 builds in a composite

### Open issues

Reconstructing proper `ProgressLogger` calls from `org.gradle.tooling.ProgressListener` information is troublesome because some information is lost. 

The progress events from the participant builds are received the `ProgressListener` interfaces. It contains only a single method `statusChanged` which accepts a `ProgressEvent` that only has a single method `getDescription`.
However creating events with `ProgressLogger` would require separate calls to `start` and `completed`. How do we find out what status change is a start and which is a complete?

The current `org.gradle.tooling.internal.consumer.parameters.ProgressListenerAdapter` implementation shows the current way should ProgressListener events are created. 

```
class ProgressListenerAdapter implements ProgressListenerVersion1 {
    private final ListenerBroadcast<ProgressListener> listeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final LinkedList<String> stack = new LinkedList<String>();

    ProgressListenerAdapter(List<ProgressListener> listeners) {
        this.listeners.addAll(listeners);
    }

    public void onOperationStart(final String description) {
        stack.addFirst(description == null ? "" : description);
        fireChangeEvent();
    }

    public void onOperationEnd() {
        stack.removeFirst();
        fireChangeEvent();
    }

    private void fireChangeEvent() {
        final String description = stack.isEmpty() ? "" : stack.getFirst();
        listeners.getSource().statusChanged(new ProgressEvent() {
            public String getDescription() {
                return description;
            }
        });
    }
}
```
When the operation ends, a statusChange event is created for the previous operation. Using this assumption, it would be possible to reconstruct the events if the descriptions are unique. Could we simply assume that they are unique?
 