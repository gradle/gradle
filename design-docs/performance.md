# Dependency resolution

## Cache dependency descriptors. Useful for regular builds. Very useful for daemon builds as the daemon can keep the descriptors in memory.

### User visible changes

-Faster builds, especially with hot daemon
-Create a property that allows turning off this feature

### Sad day cases

-someone tinkers with the artifact cache (deletes file, etc) while the daemon has hot cache

### Test coverage

-changing modules are cached only within a given build
-dynamic versions are cached only within a given build
-static versions are cached forever (until daemon exits)
-modules/artifacts from different repositories are separated
-building with --refresh-dependencies throws away cached data

### Implementation plan

-Cache should use soft maps (see guava map maker)
-Use ModuleVersionRepository
-Cache's state should be based on TopLevelProjectRegistry
-Document the breaking changes: we no longer check local repos with every resolve. The remote repo cache may no longer expire in the middle of the build.
-For descriptor caching we should cache states like: missing(), probablyMissing(), resolved()

### Questions

-sensitive to --offline builds?

# Multi-process locking

Potential spikes/stories:

1. improve our locking implementation so that we can hold the lock across long running operations and release it only if it is required by another process.
The potential downside is that we will make it more expensive to acquire the lock when it's contented. Mostly this would affect the artefact cache, but we can offset this by caching stuff in memory. Or use a different lock implementations for those things that are likely to be shared by multiple processes and for those things that are unlikely to be shared.

Potential implementation plan:
    -first process takes lock, writes address, hangs on to the lock
    -other process reads the address and sends a message 'I may need the lock'
    -after receiving this message, the first process enters 'share lock' mode

2. Switch the btree implementation so that it is append-only, so that we can read from multiple threads and processes without locking. We'd only need to serialise the writes (and the periodic garbage collection). We can get rid of the free space tracking from the implementation, which will make writing faster.
3. Make our locking implementation more efficient for the btrees, so that we can avoid an additional read and write on open and on close. We might look at some native synchronisation primitives, too, now that we can.

# Daemon

Potential spikes/stories:

1. Daemon rebuilds and caches the model after the build. This way, next time we run the build, the configured model is already built and configuration time is zero.
Tasks selected for execution also determine the model.
We'd need to start watching for changes to the files that are inputs to the model.
This includes external classpath dependencies that can change remotely, all the source files of buildSrc and its dependencies (and its model inputs),
the build environment settings in various places, and so on.

Implementation notes
    -projects can declare inputs for model caching
    -the feature is not enabled by default (can be turned on)

2. Cache some or all of the task history in memory across builds

# Task history

Potential spikes/stories:

1. Make the task history persistence format more efficient. We're still using Java serialisation for this.
This is an easy win and we saw some nice improvements when we did this for the artefact meta-data.
2. Make reading and writing task history more efficient.
We're doing 6 reads to load the history (3 x index lookups and 3 x data chunks),
and 7 reads and 8 writes to write the history (4 x reads and 4 x writes for the ids, 3 x index lookups, 3 x index writes and 3 x data chunk writes).
Instead, we should stream the history, so that for reading there's 1 index lookup and one or more data chunk reads (one for most tasks)
and for writing there's 1 index lookup, one index write and one or more data chunk writes (one for most tasks).
3. I came across a task that had a zipTree on input and up-to-date calculation took 30 minutes.
It's because the hashes are taken every individual file inside of the zipTree and in my case the zip was 0.5g.

# Other

Potential spikes/stories:

1. Don't scan the output directories at the end of the task execution, for the compile tasks.
The compile task knows exactly what its output files were. This only helps for builds where the compilation is out-of-date.
2. More profiling of configuration time to look for hot-spots.
3. Push implicit plugin application, so that plugins are only applied at configuration time when they are required.
4. Only create those tasks that are required for the build.
5. Only configure those domain objects that are required for the build.
6. Separate the task up-to-date checks from executing the actions, so that we can be resolving dependencies and doing up-to-date checks in parallel to executing the actions.
7. parallel configuration.