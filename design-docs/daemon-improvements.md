To make daemon more and more stable; better working on windows environments; better behave for unhappy scenarios.

# Info

This spec is always work in progress. It is aimed to help us design the implementation of the daemon fixes.

# Tasks

## Daemon should attempt to remove its entry from the registry on JVM exit.

I'm planning to add another shutdown hook close to the where we create
the registryUpdater object. I might add a new type DaemonShutdown so
that it's easier to see what we're doing and that there are 2 shutdown
hooks. The hook will execute registryUpdater.stop()

# Stories

# All client and daemon reads on the connection should have a timeout.

Extend our Connection infrastructure so that it allows receiving with timeout.

Connection.receive(timeoutValue, timeoutUnits)

# Daemon should exit when its entry is removed from the registry.

    1. Simple implementation:
    DaemonStateCoordinator/awaitStop.
    Instead of waiting until the daemon timeout I'll be waiting for say
    1 minute. It has to be configurable in some way so that I can
    integ-test it.
    I'll another check: missingDeamonAddress(), if missing then stop() the daemon.

    2. More robust option:
    Feels like it might be better to have some service that can run things at scheduled times (a ScheduledExecutorService, say), and just schedule a registry check to run every few minutes. If it notices the entry has gone, it just calls requestStop() on the coordinator and the daemon will stop when it becomes idle. This way we keep this concept entirely out of the coordinator.
    I'd think about implementing the idle timeout in a similar way - it's just another sentinel that is scheduled to run a certain time after a build command finishes. If the daemon is still idle, it calls forceStop().
    There's a bunch of other things we want to do periodically in the daemon - checking for changes to the model, clean up the dependency cache, check if repositories are online, check for new versions of dependencies, that kind of thing.