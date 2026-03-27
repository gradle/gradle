# Plan: File-Level Parallelization of DirectorySnapshotter

## Context

`DirectorySnapshotter` walks directory trees sequentially using `Files.walkFileTree`, hashing each file one at a time. For directories with many files (e.g., 10k+ in node_modules, build outputs), the dominant cost is `hasher.hash()` (file I/O + MD5). Parallelizing file hashing within each directory can significantly reduce snapshot time, especially on Windows where per-file I/O latency is higher.

PR #37305 parallelizes at the **directory tree level** (sibling dirs forked via ForkJoinPool). This plan takes a different approach: **parallelize file hashing within each directory** while keeping tree traversal sequential, using Gradle's `ManagedExecutor` infrastructure.

## Analysis of PR #37305

**What it does well:**
- Replaces `Files.walkFileTree` with `Files.list()` (gives control over traversal)
- Handles symlink cycle detection without the parent directory deque
- Replaces `RelativePathTracker` with explicit `List<String>` segments

**Issues:**
1. **Creates `new ForkJoinPool()` per `snapshot()` call** - expensive, not lifecycle-managed
2. **Files within a directory are still sequential** - misses the biggest optimization (file hashing is the dominant cost)
3. **Each ParallelPathVisitor has its own builder** - more memory/complexity
4. **No threshold** - every directory spawns RecursiveTasks regardless of size
5. **ForkJoinPool is not used anywhere in Gradle** - goes against architecture (Gradle uses `ExecutorFactory` + `ManagedExecutor`)
6. **Test behavior changes** - unfiltered subsnapshot ordering and error message format changed

## Design

### Core Idea

Replace `Files.walkFileTree` with manual recursive traversal via `Files.list()`. Within each directory:

1. List children, read attributes, categorize (files vs dirs vs symlinks)
2. Apply filters via `shouldVisitFile()` on the main thread (touches builder)
3. If file count >= `PARALLEL_FILE_THRESHOLD`: submit hashing tasks to `ManagedExecutor`, main thread hashes one file itself (work stealing equivalent)
4. Process symlinks-to-directories sequentially
5. Recurse into child directories sequentially (DFS)
6. Join file hash results from step 3, add to builder
7. `builder.leaveDirectory()`

```
Directory processing (main thread):
  enterDirectory()
  |
  Files.list() -> categorize children
  |
  +-- Filter files (shouldVisitFile)         [sequential - touches builder]
  +-- Submit N-1 file hashes to executor     [parallel on worker threads]
  +-- Hash 1 file on main thread             [work stealing equivalent]
  +-- Handle symlinks-to-dirs                [sequential]
  +-- Recurse into subdirectories            [sequential DFS]
  +-- Join file hash futures                 [main thread waits/collects]
  +-- Add file snapshots to builder          [sequential]
  |
  leaveDirectory()
```

### Key Design Decisions

#### 1. ManagedExecutor instead of ForkJoinPool

Use Gradle's `ExecutorFactory` to create a `ManagedExecutor`:
- Properly lifecycle-managed (auto-stopped when factory stops)
- Consistent with all other parallel work in Gradle
- No ForkJoinPool (Gradle has zero ForkJoinPool usage - intentional architectural choice)
- `ExecutorFactory` is `@ServiceScope(Scope.Global)`, already available in the service graph

The `DirectorySnapshotter` constructor gains an `@Nullable ManagedExecutor` parameter:
- When non-null: parallel file hashing enabled
- When null: fully sequential (backward-compatible, easy for tests)

#### 2. Threshold: `PARALLEL_FILE_THRESHOLD = 20`

Directories with fewer than 20 files are processed sequentially. Rationale:

| Files | Sequential cost | Parallel overhead | Benefit |
|-------|----------------|-------------------|---------|
| 5     | 0.5-2.5ms      | ~50-100us         | Minimal, not worth scheduling |
| 20    | 2-10ms         | ~100-200us        | Clear win (~3-4x on 4 cores) |
| 100   | 10-50ms        | ~500us            | Large win |

At 20 files, the parallelism benefit (overlapping I/O + MD5) clearly exceeds the `ManagedExecutor` submission overhead (task object allocation + blocking queue enqueue + thread wakeup). Below 20, the overhead-to-benefit ratio is unfavorable due to:
- `Future` object allocations
- BlockingQueue lock acquisition per task
- Thread wakeup latency for idle workers (~1-10us each)

#### 3. Main Thread Work (Work Stealing Equivalent)

Since `ManagedExecutor` doesn't support ForkJoinPool-style work stealing, we achieve the same effect manually:
- Fork N-1 file hash tasks to the executor
- Execute 1 file hash on the current/main thread
- Then call `Future.get()` on the submitted tasks

This ensures the main thread always does useful work instead of idle-waiting. For a directory with 20 files on 4 cores: main thread hashes ~1 file while 3 workers hash ~19.

#### 4. Builder Remains Single-Threaded

`FilteredTrackingMerkleDirectorySnapshotBuilder` is NOT thread-safe (uses `ArrayDeque`). All builder interactions stay on the main thread:
- `enterDirectory()`, `leaveDirectory()`, `visitLeafElement()`, `visitDirectory()`, `markCurrentLevelAsFiltered()` - all main thread
- Only `snapshotFile()` runs on worker threads (pure function: reads file, returns snapshot object)

#### 5. Symlink Handling

- **Symlinks to files**: target attrs resolved on main thread, then included in parallel hash batch
- **Symlinks to directories**: followed sequentially on main thread (recursive sub-traversal)
- **Broken symlinks**: `MissingFileSnapshot` created on main thread (no hashing needed)

### Thread Safety Audit

| Component | Thread-Safe? | Notes |
|-----------|:---:|-------|
| `FileHasher.hash()` | Yes | Stateless, stack-local FileInputStream |
| `DefaultStreamHasher` | Yes | ArrayBlockingQueue buffer pool |
| `Interner.intern()` | Yes | Guava guarantee |
| `previouslyKnownSnapshots` | Yes | ImmutableMap, read-only |
| `Statistics.Collector` | Yes | AtomicLong counters |
| `symbolicLinkMapping` | Yes | Immutable after creation |
| Builder | **No** | Kept single-threaded by design |
| `unfilteredSnapshotRecorder` | N/A | Called only from builder path (main thread) |

### Small Directories: Why Skip Parallelization

For directories with < 20 files, parallelization adds overhead with minimal benefit:
- Each `Future` submission: object alloc (~100-200 bytes) + queue lock (~50ns) + potential thread wakeup (~1-10us)
- For 5 files, total overhead: ~5-50us
- For 5 files, total sequential cost: ~0.5-2.5ms
- The overhead is small in absolute terms but the parallelism benefit is also small (limited overlapping I/O)
- The threshold prevents any regression for the common case of small directories

## Files to Modify

### 1. `DirectorySnapshotter.java`
**Path:** `platforms/core-execution/snapshots/src/main/java/org/gradle/internal/snapshot/impl/DirectorySnapshotter.java`

**Changes:**
- Add `@Nullable ManagedExecutor executor` constructor parameter and field
- Replace `PathVisitor` (FileVisitor) with `DirectoryVisitor` (plain class with recursive method)
- Replace `Files.walkFileTree` with `Files.list()` per directory
- Add `hashFilesInParallel()` method using `executor.submit()` + `Future.get()`
- Add `FileToSnapshot` helper (path + name + attrs + accessType)
- Replace `RelativePathTracker` with explicit `List<String> relativePathSegments`
- Replace `parentDirectories` Deque with `Set<String>` for cycle detection
- Add `PARALLEL_FILE_THRESHOLD = 20` constant

### 2. `DefaultFileSystemAccess.java`
**Path:** `platforms/core-execution/snapshots/src/main/java/org/gradle/internal/vfs/impl/DefaultFileSystemAccess.java`

**Changes:**
- Add `ExecutorFactory` constructor parameter
- Create `ManagedExecutor` for directory snapshotting
- Pass executor to `DirectorySnapshotter` constructor (lines 81 and 241)

### 3. Test files (constructor signature update)
- `DirectorySnapshotterTest.groovy` (line 55) - pass `null` executor
- `DirectorySnapshotterStatisticsTest.groovy` (line 39) - pass `null` executor
- `DirectorySnapshotterAsDirectoryWalkerTest.groovy` - pass `null` executor, adjust for non-deterministic visit order
- `DefaultSnapshotHierarchyTest.groovy` (line 52) - pass `null` executor

### 4. Service wiring
- Ensure `ExecutorFactory` is available where `DefaultFileSystemAccess` is created
- May need to update service registration / module configuration

## Implementation Sketch

```java
// DirectorySnapshotter constructor
public DirectorySnapshotter(
    FileHasher hasher,
    Interner<String> stringInterner,
    Collection<String> defaultExcludes,
    DirectorySnapshotterStatistics.Collector collector,
    @Nullable ManagedExecutor executor  // NEW
) {
    this.hasher = hasher;
    this.stringInterner = stringInterner;
    this.defaultExcludes = new DefaultExcludes(defaultExcludes);
    this.collector = collector;
    this.executor = executor;
}

// Inside DirectoryVisitor - parallel file hashing
private List<FileSystemLeafSnapshot> snapshotFiles(List<FileToSnapshot> files) {
    if (executor == null || files.size() < PARALLEL_FILE_THRESHOLD) {
        // Sequential path
        List<FileSystemLeafSnapshot> results = new ArrayList<>(files.size());
        for (FileToSnapshot f : files) {
            results.add(snapshotFile(f.path, f.internedName, f.attrs, f.accessType));
        }
        return results;
    }

    // Parallel path: submit N-1 to executor, run 1 on current thread
    List<Future<FileSystemLeafSnapshot>> futures = new ArrayList<>(files.size() - 1);
    for (int i = 1; i < files.size(); i++) {
        FileToSnapshot f = files.get(i);
        futures.add(executor.submit(() ->
            snapshotFile(f.path, f.internedName, f.attrs, f.accessType)));
    }

    // Main thread does one file
    List<FileSystemLeafSnapshot> results = new ArrayList<>(files.size());
    results.add(snapshotFile(files.get(0).path, files.get(0).internedName,
                             files.get(0).attrs, files.get(0).accessType));

    // Collect parallel results
    for (Future<FileSystemLeafSnapshot> future : futures) {
        try {
            results.add(future.get());
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
    return results;
}
```

## Verification

1. **Unit tests:**
   ```
   ./gradlew :snapshots:test --tests "DirectorySnapshotterTest"
   ./gradlew :snapshots:test --tests "DirectorySnapshotterAsDirectoryWalkerTest"
   ./gradlew :snapshots:test --tests "DirectorySnapshotterStatisticsTest"
   ./gradlew :snapshots:test --tests "DefaultSnapshotHierarchyTest"
   ```

2. **Snapshot determinism:** Same directory must produce identical hash regardless of parallelism. The `MerkleDirectorySnapshotBuilder` sorts children by name, so insertion order doesn't matter.

3. **Sequential fallback:** Pass `null` executor - behavior must be identical to current implementation.

4. **Broader test:**
   ```
   ./gradlew :snapshots:quickTest
   ```
