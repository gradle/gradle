# Incremental Build Improvements

This spec defines some improvements to improve incremental build and task up-to-date checks

# General Speed-ups

These are general speed ups that improve all builds.

## Story: Speed up File metadata lookup in task input/output snapshotting ✔︎

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

## Story: Add caching to Specs returned from PatternSet.getAsSpecs()

Evaluating patterns is a hotspot in directory scanning. The default excludes patterns 
contains 28 entries. Checking all rules for each file sums up in a lot of operations. 
Adding caching will improve performance of subsequent incremental builds.

### Test coverage

TBD

# Feature: Use source file dependency information in up-to-date checks

## Story: Perform dependency analysis on source files separately from compilation

- Create a separate "figure out dependencies" step in the native plugins
- Make include paths @Inputs for the compile task.  
- Create the new "extract dependencies" task.  
- Wire together everything
- Use @InputFiles for the list of includes (but keep the incremental compiler doing what it does now for staleness)

## Test coverage 

- Change in include path causes recalculation of dependencies and recompilation
- Removing dependency information causes recalculation when compiling
- Change in source file causes recalculation of dependencies
- Change in header file does not cause recalculation
- Test header parsing/extraction

## Story: Reuse dependency information in incremental native compiler

- Remove #include parsing from incremental compiler
- Use dependency information from above
- Still do source file staleness checks in the incremental compiler

## Test coverage 

- Existing test coverage should cover most cases
- Remeasure performance/profiling after this is complete

## Story: Reuse native source file dependency information within a build

Gradle parses each source file to determine the source file dependencies. 
Currently this is cached on a per-task basis, meaning it is recalculated many 
times when building multiple variants and compiling test suites. We should 
instead move this to a cache that is shared across all tasks for a build, 
and kept in memory across builds.

# Uncategorized

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

### Test coverage

TBD

## Story: Reduce number of directory scans in up-to-date checks

TBD

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