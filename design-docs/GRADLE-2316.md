# GRADLE-2316 Received invalid response from the daemon

Issue reported in 2012 in 1.0-rc-3.  Seen again with 2.4 and 2.6.

## Summary

After the command-line process connects to the Gradle Daemon, it sends a 'Build' message to start
a build in the daemon.  Usually, this is followed up with a 'BuildStarted' message from the daemon
and then status/output messages.  At the end of the build, the daemon responds with a Result and 
the CLI sends a 'Finished' message.

After sending the Build command, the CLI can understand messages of type Failure, DaemonUnavailable,
BuildStarted, Result, OutputMessage, and BuildEvent.  It also handles the daemon disappearing
as receiving nothing (null).  In cases where the daemon disappears suddenly or is unavailable, the
CLI will retry the build with a different or new daemon.  Receiving an unexpected message throws an
unrecoverable IllegalStateException, which stops the build.

For this issue, the sequence appears to be:
- CLI connects to daemon
- CLI sends Build message
- CLI receives Build message

Since the Build message is unexpected, the build stops.

## Theories

### Two CLIs are talking to each other (instead of a daemon)

- CLI processes don't create a listen port
- The Build messages contain a UUID and working directory.  The sent and received messages appear to be identical.

### Daemon can intentionally respond with the same message sent to it

- Cannot find any evidence this is possible.
- DefaultIncomingConnectionHandler is the only class that has access to the underlying
SocketConnection.  It never sends messages and everything else in the daemon uses DaemonConnection instead
- DaemonConnection provides no way to send a generic Message directly back to the client.

### Kryo serialization reuses state when serializing/deserializing

- Found strange behavior when attempting to read after EOF on a socket read.  Message received before EOF is received again.
- Input and output use different Kryo serializers (so it seems unlikely they share any state)
- Real DaemonClient behavior is to treat EOF as the daemon disappearing, so it wouldn't attempt to receive() after EOF
- Captured logs don't appear to show the CLI receiving anything other than Build message

### Socket is reused from old daemon connection by new CLI connection and still contains Build message in buffers

- Attempted to dispatch Build message to daemon many times
- On daemon side, close the connection
- On client side, open a new SocketChannel and attempt to bind to daemon socket and read
- Most of the time, the socket would appear to be in-use (even after turning on address reuse)
- Rarely, the socket would bind and nothing could be read

### IPv6 and IPv4 causing some sort of port redirect?

- The CLI and daemon connect over localhost with IPv6, but both will attempt connections with IPv4
- Trying builds with -Djava.net.preferIPv4Stack=true to see if failure remains

### Non-blocking SocketConnection allows reads to occur during writes

- Attempted to force this to occur by writing continuously and forcing the read selector to wakeup() and read

### Concurrent writers 

- Concurrent writer threads to SocketConnection allow sent messages to be read as received messages
- For the daemon, all writes go through SynchronizedDispatchConnection
- For the CLI, all writes go through DaemonClientConnection
- Both appear to be synchronized, so should allow only one write at a time
- No concurrent writers exist on the CLI side until after receiving BuildStarted

### Impersonated daemon connection

- A rogue process that reflects messages back to the sender