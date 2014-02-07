To make daemon more and more stable; better working on windows environments; better behave for unhappy scenarios.

# Info

This spec is always work in progress. It is aimed to help us design the implementation of the daemon fixes.

# Stories


## All client and daemon reads on the connection should have a timeout.

Extend our Connection infrastructure so that it allows receiving with timeout.

Connection.receive(timeoutValue, timeoutUnits)

## Daemon should exit when its entry is removed from the registry.

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

## Native daemon client

Use our C support to build a native equivalent to `gradle --daemon`.

## Daemon feature is “usable” when under memory pressure

Currently, the daemon has serious problems when memory pressure occurs. 
When under pressure, the daemon process exhibits GC thrash. 
Please see [this forum post](http://forums.gradle.org/gradle/topics/gradle_daemon_becomes_very_slow_when_the_heap_is_nearly_out_of_memory_its_running_full_gcs_almost_back_to) for a discussion.

One hypothesis for this is the use of weak reference caches, particularly in the Groovy metaclass system where meta class instances are held in a weak reference cache.
Note that this is not necessarily a problem with the daemon, as it would also apply to the non daemon case. 
However, it is exacerbated by the daemon leaking memory, thereby increasing the chance of a memory pressure situation occurring.

The correct outcome would be for the build to fail quickly instead of hanging in GC thrash limbo.
This could be done by either detecting or predicting GC thrash and terminating early.