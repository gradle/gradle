# Build execution model

At the highest level, Gradle's execution model is quite simple:

Below is the protocol in some more detail:

1. The client looks for a compatible idle daemon. If there isn't one, it starts a new daemon.
2. The client connects to the idle daemon and sends it a request to do some work. If the daemon is no longer running, the client starts again.
3. If the daemon is not able to run the request, for example it is already running a request or is shutting down, it rejects the request. The client starts again.
4. The daemon runs the request, sending back data such as logging output or tooling API events and intermediate models while doing so.
5. The daemon sends the result back. For some requests, this might be a simple success/failure result, and for others this might also include a more complex object, such as a tooling API model.
6. The client disconnects from the daemon.

