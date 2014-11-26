Issues:

1. Logging generated in the consumer and provider is client process' `System.out` instead of client stream and does not honour the operation's
log level. Some examples:
    - Distribution download logging (written to directly `System.out`)
    - slf4j output in the provider.
    - slf4j output in the consumer.
2. A failure in the daemon, provider or consumer is not rendered to client stream in the same way that failures in the build are. Some examples:
    - Daemon cannot be started.
    - Distribution download is cancelled.
    - Invalid distribution URL.

Improvements:

1. Add progress reporting for connecting to and starting the daemon.
