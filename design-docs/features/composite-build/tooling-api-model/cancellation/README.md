## Tooling client cancels composite model request

### Overview

Cancellation should be supported for composite model requests in the
same way as it is supported for ordinary project connection model requests.

### API

This story doesn't introduce a new API. The existing `org.gradle.tooling.ModelBuilder` interface extends from `ConfigurableLauncher` interface which contains the
`withCancellationToken` method. This method can be used to specify the cancellation token for the request. This should be supported for composite model requests as well.

### Implementation notes

In the composite implementation, the cancellation token should be passed on to requests that are made to the composite
participant builds. Before making new requests to participants, the composite request should be cancelled if the cancellation token is set.
BuildCancelledException should be thrown when the composite model request is cancelled.

### Test coverage

- check that composite requests get cancelled when the token is initially set to cancelled
- check that requests get cancelled when the token is cancelled while the request is being handled

### Documentation

### Open issues
