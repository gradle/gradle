# Daemon feature is “usable” when under memory pressure

Currently, the daemon has serious problems when memory pressure occurs.
When under pressure, the daemon process exhibits GC thrash.
Please see [this forum post](http://forums.gradle.org/gradle/topics/gradle_daemon_becomes_very_slow_when_the_heap_is_nearly_out_of_memory_its_running_full_gcs_almost_back_to) for a discussion.

One hypothesis for this is the use of weak reference caches, particularly in the Groovy metaclass system where meta class instances are held in a weak reference cache.
Note that this is not necessarily a problem with the daemon, as it would also apply to the non daemon case.
However, it is exacerbated by the daemon leaking memory, thereby increasing the chance of a memory pressure situation occurring.

The correct outcome would be for the build to fail quickly instead of hanging in GC thrash limbo.
This could be done by either detecting or predicting GC thrash and terminating early.

# Memory leaks

Memory leaks are unavoidable because:

- Gradle runs 3rd party code that may incur mem leaks
- Gradle ships with 3rd party tools, many of them quite large (groovy) and they may contain mem leaks
- Gradle uses jdk and it can have bugs that lead to mem leaks ;)
- Gradle itself can have mem leaks

1. First front of a battle against the leaks is fixing them in tools we control and reporting bugs to tools we don't control.
2. Second front is to make the daemon smarter. Daemon should know the footprint and perform actions based on that knowledge.
   Those actions could be: exit/expire daemon quickly, restart eagerly, inform the user about memory problem, etc.

# Implementation plan

## Daemon makes the user aware of memory usage

It feels important to let the user know about certain stats of the daemon.
It would help building stronger confidence in the daemon and provide information for memory tweaking.

### User visible changes

When building with the daemon there is an elegant lifecycle message informing about the daemon status

"Starting new daemon process for this build"
"Executing 22nd build in daemon [uptime: 12 mins, memory: 64M of 3.8G]"

## Daemon exits early before vm gets into gc thrashing

## Daemon exits after the build if it suspects that the next build will put vm into gc thrashing

## Daemon restarts instead of exiting when exit reason is one of above

# Ideas

- Daemon automatically prints out gc log events to the file in daemon dir. Useful for diagnosing offline.
- Daemon writes gc log events and analyzes them:
    - Understands and warns the user if throughput is going down
    - Knows when gc is about to freeze the vm and and exits eagerly providing decent message to the user
    - tracks memory leaks
- Daemon scales memory automatically by starting with some defaults and gradually lowering the heap size
- Daemon knows why the previous daemon exited. If it was due to memory problems a message is shown to the user.

# Open issues

This section is to keep track of assumptions and things we haven't figured out yet.
