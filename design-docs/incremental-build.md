# Incremental Build Improvements

This spec defines some improvements to improve incremental build and task up-to-date checks

# General Speed-ups

These are general speed ups that improve all builds.

## Story: Speed up File metadata lookup in task input/output snapshotting 

File metadata operations .isFile(), .isDirectory(), .length() and .lastModified are 
hotspots in task input/output snapshotting.

The Java nio2 directory walking method java.nio.file.Files.walkFileTree can pass the file 
metadata used for directory scanning to "visiting" the file tree so that metadata 
(BasicFileAttributes) doesn't have to be re-read.

### Implementation

- For JDK7+ with UTF-8 file encoding, use a nio2 file walker implemention. ✔︎
    - Cache isDirectory()/getSize()/getLastModified() in FileVisitDetails from BasicFileAttributes gathered from walking ✔︎
- Otherwise, use default file walker implementation (current behavior). ✔︎
    - Use a caching FileVisitDetails for getSize()/getLastModified() to cache on first use. ✔︎
    - Maybe reuse isFile/isDirectory result from the walker implementation ✔︎
- Replace calls to getFiles() in DefaultFileCollectionSnapshotter with a visitor ✔︎

### Test coverage

- Test that correct implementation is chosen for JDK platform and file encoding ✔︎
- Test that a file walker sees a snapshot of tree even if the tree is modified after walking has started. ✔︎
- Generate file tree and walk with JDK7+ file walker and non-nio2 file walker. Attributes and files should be the same for both. ✔︎
- Performance gains will be measured from existing performance tests. ✔︎
- Expect existing test coverage will cover behavior of input/output snapshotting and file collection operations. ✔︎

## Story: Changes to reduce byte/char array allocations and array copying

- Just do it

## Story: Changes for adjusting collection sizes when final size is known

- Just do it

## Story: Reduce the in-memory size of the task history cache by interning file paths

### Implementation

- Use Guava's [`Interners.newWeakInterner()`](http://google.github.io/guava/releases/18.0/api/docs/com/google/common/collect/Interners.html#newWeakInterner%28%29) to create a cache `StringInterner` for sharing the file path Strings. Place this cache in `GlobalScopeServices` so that the instance lives across multiple builds in the daemon.
- use the `StringInterner` to intern all duplicate path names contained in `fileSnapshots`, `taskArtifacts`, `outputFileStates` and `fileHashes` caches.
- Implementation can be based on the solution developed in the spike. The commit is https://github.com/gradle/gradle/commit/d26d4ce1098e0eee9896279cbeabefb1ca3e871c .

### Test coverage

- Add basic unit test coverage for StringInterner
  - interning different string instances with similar content return the first instance that was interned
  - allows calling method with null, returns null in that case

## Story: Add caching to Specs returned from PatternSet.getAsSpecs()

Evaluating patterns is a hotspot in directory scanning. The default excludes patterns 
contains 28 entries. Checking all rules for each file sums up in a lot of operations. 
Adding caching will improve performance of subsequent incremental builds.

### Test coverage

TBD

## Story: High number of UnknownDomainObjectExceptions when resolving libraries in native projects.

TBD

## Story: Add "discovered" inputs

TBD

# Unprioritized

## Story: Reuse native source file dependency information within a build

Gradle parses each source file to determine the source file dependencies. 
Currently this is cached on a per-task basis, meaning it is recalculated many 
times when building multiple variants and compiling test suites. We should 
instead move this to a cache that is shared across all tasks for a build, 
and kept in memory across builds.

## Story: Inline the data from the file hash cache into the task history cache

Change `DefaultFileCollectionSnapshotter` to store (length, last-modified, hash)
in the `FileCollectionSnapshot` implementation it creates. This is referenced by
the task history cache.

When deciding whether something has changed, iterate over the elements of the
`FileCollectionSnapshot` and use the (length, last-modified, hash) from the
snapshot. Do not fetch this information from the file hash cache. If something
has changed, hash it, and add the (length, last-modified, hash) tuple to the
file hash cache. Also make a copy of the snapshot and update it with this new
entry. This copy will be used as the new snapshot in the task history.

What this means is that file hash cache is used only for new and modified files,
which means there’s no point caching these entries in-memory (except perhaps for
the duration of a build). For unmodified files, there’s never a cache miss. When
nothing has changed for a snapshot, we also don’t need to hold a lock on the
cache, which means less work and better concurrency when most snapshots are
unmodified.

### Test coverage

TBD

## Story: Reduce number of directory scans in up-to-date checks

TBD

## Story: Reduce the in-memory size of the task history cache

1. Discard history that will never be used, eg for tasks that no longer exist,
or that is unlikely to be used, eg for tasks in builds other than the current.
2. Keep a hash of the file snapshots in memory rather than the file snapshots
themselves. It’s only when we need to know exactly which files have changed that
we would need to load up the snapshot.
3. Do something similar for the task properties as well.
4. Reduce the cost of a cache miss, by improving the efficiency of serialisation
to the file system, and reducing the cache size.
5. Reduce the cost of a cache miss, by spooling to an efficient transient second
level cache, and reducing the cache size.

## Open Issues

- Check behavior of Prebuilt Libraries with large number of headers (https://discuss.gradle.org/t/native-performance-issue-with-no-op-builds-with-boost-as-a-dependency)
- See incremental-build-old.md for other ideas and specific issues.
- Profiler support: Skip warm-up or have explicit opt-in for warm up profiling?
  - Conditionally enabling the profiler in the code by using Yourkit API.
- Profiler support: For cross-version tests, skip all versions except the current?
- Profiler support: Do we care about conditionally profiling the CLI and daemon processes (such that the profiling options should be configurable for each)?

## Story: TBD

TBD

### Test coverage

- TBD


## Background information about the in-memory caches

File path Strings are duplicated in memory in different in-memory caches. The different in-memory caches in Gradle are:
  - [`fileSnapshots` used in `CacheBackedFileSnapshotRepository`](https://github.com/gradle/gradle/blob/b96e5802f277872b3484947ce9970180b139563a/subprojects/core/src/main/groovy/org/gradle/api/internal/changedetection/state/CacheBackedFileSnapshotRepository.java#L29)
    - key is Long id
    - value is `FileCollectionSnapshot`, serialized with `DefaultFileSnapshotterSerializer`
  - [`taskArtifacts` used in `CacheBackedTaskHistoryRepository`](https://github.com/gradle/gradle/blob/b96e5802f277872b3484947ce9970180b139563a/subprojects/core/src/main/groovy/org/gradle/api/internal/changedetection/state/CacheBackedTaskHistoryRepository.java#L37)
    - key is String, a task path
    - value is `CacheBackedTaskHistoryRepository.TaskHistory`, serialized with `CacheBackedTaskHistoryRepository.TaskHistorySerializer`
  - [`outputFileStates` used in `OutputFilesCollectionSnapshotter`](https://github.com/gradle/gradle/blob/b96e5802f277872b3484947ce9970180b139563a/subprojects/core/src/main/groovy/org/gradle/api/internal/changedetection/state/OutputFilesCollectionSnapshotter.java#L54)
    - key is String, a output directory path
    - value is Long, a random id
  - [`fileHashes` used in `CachingFileSnapshotter`](https://github.com/gradle/gradle/blob/b96e5802f277872b3484947ce9970180b139563a/subprojects/core/src/main/groovy/org/gradle/api/internal/changedetection/state/CachingFileSnapshotter.java#L35)
    - key is String, a file path
    - value is `CachingFileSnapshotter.FileInfo`, serialized with `CachingFileSnapshotter.FileInfoSerializer`
  - [`compilationState` in nativeplatform's `DefaultCompilationStateCacheFactory`](https://github.com/gradle/gradle/blob/master/subprojects/language-native/src/main/java/org/gradle/language/nativeplatform/internal/incremental/DefaultCompilationStateCacheFactory.java#L28)
    - key is String, a task path
    - value is `org.gradle.language.nativeplatform.internal.incremental.CompilationState`, serialized with `CompilationStateSerializer`

The in-memory caches are a decorator for the underlying persistent cache. In-memory cache decorators are created in InMemoryTaskArtifactCache and live as part of that instance which is in the `GlobalScopeServices`. Persistent cache instances are typically part of the [`TaskExecutionServices`](https://github.com/gradle/gradle/blob/ef18b81c9ee03cf58a49e69a3c6f5abb4557d8f7/subprojects/core/src/main/groovy/org/gradle/internal/service/scopes/TaskExecutionServices.java#L73-L81) which are in `GradleScopeServices` (instances live for the duration of a single build execution). In-memory cache decorators are only used in the Gradle daemon process.

### Relationship of `fileSnapshots` and `taskArtifacts` caches

The `taskArtifacts` cache contains `TaskHistory` entries. Each `TaskHistory` can contain multiple task executions (`CacheBackedTaskHistoryRepository.LazyTaskExecution`). Each `LazyTaskExecution` contains it's input and output filesnapshots (`FileCollectionSnapshot`). 
Input and output instances are lazily loaded from the `fileSnapshots` cache when the `TaskHistory` instance gets loaded in an incremental build.

The life-time of the `TaskHistory` instance can be seen in the 
[`SkipUpToDateTaskExecuter.execute`](https://github.com/gradle/gradle/blob/3a0cfd6ac94eb9db4c7884c46ef4f9e973dca114/subprojects/core/src/main/groovy/org/gradle/api/internal/tasks/execution/SkipUpToDateTaskExecuter.java#L66) method. The in-memory `TaskHistory` instance is persisted in the call to it's `update` method. This call originates from the `TaskArtifactState.afterTask` call in the `SkipUpToDateTaskExecuter.execute` method.

When the `TaskHistory` gets persisted, it adds the current task execution to the list of executions and limits the number of executions to 3 by removing any additional task executions. When the task execution (`LazyTaskExecution`) gets persisted, the input and output file snapshots get persisted in the `filesnapshots` cache. This serves at least 2 purposes: the filesnapshot don't have to be loaded when the `TaskHistory` is loaded. It also prevents updating the input and output filesnapshots to the persistent storage when the `TaskHistory` instance gets updated. When the `TaskHistory` instance gets updated, all data gets re-serialized to disk.
