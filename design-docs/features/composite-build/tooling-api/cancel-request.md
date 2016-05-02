## Tooling client cancels composite model request

### Overview

Cancellation should be supported for composite model requests and build launchers in the same way as it is supported for ordinary project connection model requests.

### API

This story doesn't introduce a new API. The existing `org.gradle.tooling.ModelBuilder` and `org.gradle.tooling.BuildLauncher` interfaces extend from `ConfigurableLauncher`, which contains the `withCancellationToken` method. This method can be used to specify the cancellation token for the request. This should be supported for composite model requests as well.

### Implementation notes

In the composite implementation, the cancellation token should be passed on to requests that are made to the composite
participant builds. Before making new requests to participants, the composite request should be cancelled if the cancellation token is set.
BuildCancelledException should be thrown when the composite model request is cancelled.

### Test coverage

- For the following composite configurations
    - A single-project, single-participant
    - A multi-project, single-participant
    - A single-project/multi-project, multi-participant
- Test should exercise model requests and task execution.
- Check that composite requests get cancelled when the token is initially set to cancelled.
  - No participant requests are started at all.
  - No tasks are executed.
- Check that participant requests get cancelled when the token is cancelled while a participant request is being processed.
  - The executing participant request gets cancelled.
  - No new participant requests are started.
  - No tasks are executed past the first participant.
  - All results from subsequent participants are failures with `BuildCancelledException`

### Documentation

- N/A

### Open issues

- N/A