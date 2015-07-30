# Dependency resolution

## Cache dependency descriptors in memory

Useful for regular builds. Very useful for daemon builds as the daemon can keep the descriptors in memory.

### User visible changes

- Faster builds, especially with hot daemon
- Create a property that allows turning off this feature

### Sad day cases

- Someone tinkers with the artifact cache (deletes file, etc) while the daemon has hot cache

### Test coverage

- Changing modules are cached only within a given build
- Dynamic versions are cached only within a given build
- Static versions are cached forever (until daemon exits)
- Modules/artifacts from different repositories are separated
- Building with --refresh-dependencies throws away cached data

### Implementation plan

- Cache should use soft maps (see guava map maker)
- Use ModuleVersionRepository
- Cache's state should be based on TopLevelProjectRegistry
- Document the breaking changes: we no longer check local repos with every resolve. The remote repo cache may no longer expire in the middle of the build.
- For descriptor caching we should cache states like: missing(), probablyMissing(), resolved()

### Questions

- Sensitive to `--offline` builds?

## Other stories

1. Use finer-grained cache locking to allow concurrent dependency resolution.
 - some objects that are build-global are used to setup resolution (ResolutionResultsStoreFactory) and they are not thread safe atm.
2. Use a Gradle artifact id rather than an Ivy Artifact to refer to an artifact to be resolved, to reduce heap usage.
3. Perform all artifact resolutions in one batch, rather than on-demand.
4. Use a binary format for cached module meta-data.
5. Perform network accesses concurrently. For example, queue up network requests and use a pool of worker threads. Traverse the graph as results are made
   available.

# Persistent caches

## Improve cache locking efficiency when running in parallel mode

Improve our locking implementation so that we can hold the lock across long running operations and release it only if it is required by another process.
The potential downside is that we will make it more expensive to acquire the lock when it's contented. Mostly this would affect the artefact cache, but we can offset this by caching stuff in memory. Or use a different lock implementations for those things that are likely to be shared by multiple processes and for those things that are unlikely to be shared.

Potential implementation plan:
    -first process takes lock, writes address, hangs on to the lock
    -other process reads the address and sends a message 'I may need the lock'
    -after receiving this message, the first process enters 'share lock' mode

## Other stories

1. Switch the b-tree implementation so that it is append-only, so that we can read from multiple threads and processes without locking. We'd only need to serialise the writes (and the periodic garbage collection). We can get rid of the free space tracking from the implementation, which will make writing faster.
2. Write back to the cache asynchronously from a worker thread.
3. Make our locking implementation more efficient for the b-trees, so that we can avoid an additional read and write on open and on close. We might look at some native synchronisation primitives, too.

# Incremental build

## Cache task history in memory across builds

Results from a spike show 30% speed improvement for the 'fully' incremental build.

### User visible changes

Faster builds when daemon used. Reasonable increase of heap consumption.

### Implementation

- provide implementation of TaskArtifactStateCacheAccess that ads in-memory caching capabilities
- expire the cache data when cache file's last modified time changes. Check for expiration before locking the file, remember the last modified before unlocking.
- the implementation can be improved in various ways (e.g. stop using the last modified, we know when cross-process lock is requested by other processes)
- the cache should have some bounds otherwise it will use a lot of memory for gigantic builds. Initially, we will cap the cache size.
- enable caching only when daemon is used

### Test coverage

- add performance tests with the daemon

## Other stories

Potential spikes/stories:

1. Don't scan the output directories at the end of the task execution, for the compile and copy tasks. These task types know exactly what their output files were.
2. Make reading and writing task history more efficient.
   Currently, we're doing 6 reads to load the history (3 x index lookups and 3 x data chunks),
   and 7 reads and 8 writes to write the history (4 x reads and 4 x writes for the ids, 3 x index lookups, 3 x index writes and 3 x data chunk writes).
   Instead, we should stream the history, so that for reading there's 1 index lookup and one or more data chunk reads (one for most tasks)
   and for writing there's 1 index lookup, one index write and one or more data chunk writes (one for most tasks).
3. When using an input `FileCollection` which is contained in some other resource (eg a `ZipTree`), then use the container resource for up-to-date checks,
   rather than the individual elements of the collection.
4. Reuse the file hash information included in the input/output file snapshots, rather than attempting to read it from the file hash cache.
5. Separate the task up-to-date checks from executing the actions, so that we can be resolving dependencies and doing up-to-date checks in parallel to executing the actions.

# Daemon

## Hint the vm to gc after build in the daemon completes

Full gc scans are unavoidable when the daemon is used. It's because at the end of the build, there will be lots of objects in the tenured space.
It's better if the full scan happens 'outside' of the user's build, because full scan pauses the entire vm.

### User visible changes

It will be hard to prove but in theory daemon builds should be more consistent and faster.

### Implementation

 - Add new DaemonCommandAction, say DaemonHygiene.
 - Slot it in the actions chain so that it is executed after the build has completed, and the user received the 'build successful' message
 - This action needs to be stateful (currently all actions are created per build request)
 - This action may perform gc at the end of the build, but not too often, say once per 2 minutes.
 - This action may perform other hygiene actions, monitor memory usage, etc.

## Other potential spikes/stories:

1. Daemon rebuilds and caches the model after the build. This way, next time we run the build, the configured model is already built and configuration time is zero.
Tasks selected for execution also determine the model.
We'd need to start watching for changes to the files that are inputs to the model.
This includes external classpath dependencies that can change remotely, all the source files of buildSrc and its dependencies (and its model inputs),
the build environment settings in various places, and so on.

Implementation notes
    -projects can declare inputs for model caching
    -the feature is not enabled by default (can be turned on)

2. Reuse build script and plugin classloaders.

# Open issues

## Disabled variants performance test

The `VariantsPerformanceTest."multiproject using variants #scenario build"` has been disabled for the
"all variants all projects" scenario.

This test caused builds to stall/crash. One example was in the 'old model' scenario: not sure if this is consistent.

The first example I can find is: https://builds.gradle.org/viewLog.html?buildTypeId=Gradle_Master_Performance_Linux&buildId=364897 but there are many subsequent failures.
