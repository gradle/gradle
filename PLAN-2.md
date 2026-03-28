# Plan: Rewrite DirectorySnapshotter from Scratch

## Context

The current parallel DirectorySnapshotter (on `asodja/parallel-snapshot` branch) suffers from GC pressure causing performance degradation over benchmark iterations. The root causes are:

1. **Two separate executors** (`hashingExecutor`: fixed pool, `traversalExecutor`: ForkJoinPool) but plain `future.get()` on ForkJoinPool threads doesn't trigger managed blocking/compensation
2. **All file hashing goes through the executor**, even for directories with 1 file -- creating a Future + lambda + ArrayList per tiny directory
3. **Wrapper objects** (`FileToSnapshot`, `DirToVisit`) allocated for every file/directory entry
4. **HashSet copies** of `parentDirPaths` for each forked subdirectory

The rewrite preserves the identical public contract and snapshot correctness while eliminating these issues.

## Architecture: Single ForkJoinPool + Two Clean Code Paths

Replace both executors with a single `ForkJoinPool`. Split into two completely separate code paths based on whether a predicate is present.

### Why ForkJoinPool with RecursiveTask?

- `task.fork()` + `task.join()` correctly triggers ForkJoinPool managed blocking/compensation (unlike plain `Future.get()`)
- Work-stealing naturally balances directory traversal across threads
- `RecursiveTask` IS the result -- no separate Future/FutureTask wrapper objects
- The current thread can do useful work (hash files) while forked subtasks run

### Class Structure

```
DirectorySnapshotter (rewritten)
  |
  +-- Fields: hasher, stringInterner, defaultExcludes, collector, forkJoinPool
  |
  +-- snapshot(...)                        // same public signature
  |
  +-- SnapshotDirectoryTask                // PARALLEL path (predicate == null)
  |     extends RecursiveTask<DirectorySnapshot>
  |
  +-- SequentialDirectoryVisitor           // SEQUENTIAL path (predicate != null)
  |     simple recursive method, all inline
  |
  +-- SymbolicLinkMapping (reuse as-is)
  +-- DefaultExcludes (reuse as-is)
  +-- SnapshotResult (keep)
```

### Eliminated Classes/Wrappers

| Eliminated | Replacement |
|---|---|
| `FileToSnapshot` | `FileEntry` (lightweight temp holder) in parallel path; no wrapper in sequential path |
| `DirToVisit` | Recurse immediately (sequential) or `SnapshotDirectoryTask` (parallel) |
| `SubmittedFileHashing` | `HashChunkTask extends RecursiveTask` for large dirs; inline for small dirs |
| `DirectoryVisitor` (unified) | Split into `SnapshotDirectoryTask` + `SequentialDirectoryVisitor` |
| `hashingExecutor` | Single `ForkJoinPool` via `ManagedForkJoinPool` |
| `traversalExecutor` | Single `ForkJoinPool` via `ManagedForkJoinPool` |

---

## Implementation Steps

### Step 0: Add `ManagedForkJoinPool` interface

The `ManagedExecutor` wraps via `AbstractExecutorService.submit()` which creates `FutureTask`, defeating ForkJoinPool's work-stealing and managed blocking. We need direct access to `ForkJoinPool.invoke(RecursiveTask)` while keeping lifecycle management.

**New interface** in `platforms/core-runtime/concurrent`:
```java
public interface ManagedForkJoinPool extends ManagedExecutor {
    <T> T invoke(ForkJoinTask<T> task);
}
```

**New implementation** in `DefaultExecutorFactory`:
```java
private class TrackedManagedForkJoinPool extends ManagedExecutorImpl implements ManagedForkJoinPool {
    private final ForkJoinPool forkJoinPool;

    TrackedManagedForkJoinPool(ForkJoinPool pool, ExecutorPolicy policy) {
        super(pool, policy);
        this.forkJoinPool = pool;
    }

    @Override
    public <T> T invoke(ForkJoinTask<T> task) {
        return forkJoinPool.invoke(task);
    }
}
```

**Update `ExecutorFactory.createWorkStealingPool()`** return type (covariant):
```java
ManagedForkJoinPool createWorkStealingPool(String displayName);
```

### Step 1: Constructor Change

**From:**
```java
public DirectorySnapshotter(FileHasher, Interner, Collection<String>, Collector,
    ExecutorService hashingExecutor, ExecutorService traversalExecutor)
```

**To:**
```java
public DirectorySnapshotter(FileHasher, Interner, Collection<String>, Collector,
    ManagedForkJoinPool forkJoinPool)
```

### Step 2: `snapshot()` Entry Point

Dispatch based on predicate:

```java
public FileSystemLocationSnapshot snapshot(String absolutePath, @Nullable predicate,
        previouslyKnownSnapshots, unfilteredSnapshotRecorder) {
    collector.recordVisitHierarchy();
    Path rootPath = Paths.get(absolutePath);
    BasicFileAttributes rootAttrs = readAttributes(rootPath, NOFOLLOW_LINKS);

    if (rootAttrs.isDirectory()) {
        if (predicate == null) {
            // PARALLEL: ForkJoinPool with RecursiveTask
            DirectorySnapshot result = forkJoinPool.invoke(  // ManagedForkJoinPool.invoke()
                new SnapshotDirectoryTask(rootPath, EMPTY_MAPPING, PersistentSet.of(), ...));
            unfilteredSnapshotRecorder.accept(result);
            return result;
        } else {
            // SEQUENTIAL: recursive on calling thread
            var visitor = new SequentialDirectoryVisitor(predicate, ...);
            SnapshotResult result = visitor.processDirectory(rootPath, ...);
            if (!result.isFiltered) unfilteredSnapshotRecorder.accept(result.snapshot);
            return result.snapshot;
        }
    } else {
        // File/symlink root handling (same as current)
    }
}
```

Key simplification: In the parallel path, `hasBeenFiltered` is never set (no predicate = nothing is filtered), so `unfilteredSnapshotRecorder` is always called exactly once at root. The `AtomicBoolean hasBeenFiltered` is eliminated from the parallel path.

### Step 3: Parallel Path -- `SnapshotDirectoryTask`

```java
private class SnapshotDirectoryTask extends RecursiveTask<DirectorySnapshot> {
    final Path dir;
    final SymbolicLinkMapping mapping;
    final PersistentSet<String> parentDirPaths;  // CHAMP trie, structural sharing via plus()
    final ImmutableMap<String, ?> previouslyKnownSnapshots;
```

**`compute()` algorithm:**

1. Check `previouslyKnownSnapshots` for cached directory snapshot -> return early
2. `PersistentSet<String> currentDirPaths = parentDirPaths.plus(dir.toString())` -- cycle detection, ~O(1) via structural sharing
3. List directory children via `Files.list(dir)`, classify into:
   - `forkedDirTasks` list (forked `SnapshotDirectoryTask` for each subdirectory)
   - `fileEntries` list (`FileEntry` holders for files to hash)
   - symlink dir tasks (forked `SnapshotDirectoryTask` for symlink targets)
4. For each child during listing:
   - **Directory**: create `SnapshotDirectoryTask`, call `task.fork()`, add to `forkedDirTasks`
   - **File**: add `FileEntry(path, name, attrs, accessType)` to `fileEntries`
   - **Symlink to dir**: fork a `SnapshotDirectoryTask` for the resolved target (with new `SymbolicLinkMapping`)
   - **Symlink to file**: add `FileEntry` with `AccessType.VIA_SYMLINK`
5. Hash files -- **adaptive strategy based on total bytes, not count**:
   - `totalBytes <= PARALLEL_BYTES_THRESHOLD || fileEntries.size() <= 1`: hash all inline
   - Otherwise: fork chunks as `HashChunkTask`, hash first chunk inline
6. Join all forked directory tasks
7. `buildDirectorySnapshot()` -- sort + Merkle hash

**File hashing -- byte-based threshold:**

The cost of hashing is proportional to file size, not file count. Using `BasicFileAttributes.size()`:

```java
private static final long PARALLEL_BYTES_THRESHOLD = 256 * 1024; // 256KB

long totalBytes = 0;
for (FileEntry f : fileEntries) totalBytes += f.attrs.size();

if (totalBytes <= PARALLEL_BYTES_THRESHOLD || fileEntries.size() <= 1) {
    // Inline: small total work or single file
    for (FileEntry f : fileEntries) children.add(snapshotFile(f));
} else {
    // Parallel: fork chunks, hash first chunk inline
    int chunkSize = Math.max(2,
        fileEntries.size() / (Runtime.getRuntime().availableProcessors() * 2));

    // Fork tasks for chunks 2..N
    List<HashChunkTask> hashTasks = new ArrayList<>();
    for (int start = chunkSize; start < fileEntries.size(); start += chunkSize) {
        int end = Math.min(start + chunkSize, fileEntries.size());
        HashChunkTask task = new HashChunkTask(fileEntries, start, end);
        task.fork();
        hashTasks.add(task);
    }

    // Hash first chunk inline on current thread
    for (int i = 0; i < Math.min(chunkSize, fileEntries.size()); i++) {
        children.add(snapshotFile(fileEntries.get(i)));
    }

    // Join remaining chunks
    for (HashChunkTask task : hashTasks) children.addAll(task.join());
}
```

**How this handles real Gradle workloads:**

| Scenario | Total bytes | Decision | Why |
|---|---|---|---|
| 5 jars (10MB each) | 50MB | parallel | Above 256KB, fork chunks for I/O overlap |
| 5 class files (5KB each) | 25KB | inline | Below 256KB, forking overhead > benefit |
| 2000 class files (5KB each) | 10MB | parallel | Above 256KB, many small files worth splitting |
| 1 huge jar (100MB) | 100MB | inline | Single file, can't split one hash |
| 20 source files (2KB each) | 40KB | inline | Below 256KB, trivial work |

ForkJoinPool work-stealing handles chunk imbalance naturally (thread finishing small files steals work from thread hashing large jar).

**Key design decisions:**
- `FileEntry` is a minimal data holder (4 fields) -- only used as temp storage between enumeration and hashing
- In the sequential path, files are hashed immediately during enumeration -- no `FileEntry` at all
- `parentDirPaths` is `PersistentSet<String>` (CHAMP trie) -- `set.plus(path)` is ~O(1) with structural sharing; siblings share the same parent reference without copying
- `relativePathSegments` is NOT tracked (only used by predicate, which is null in parallel path)
- `unfilteredSnapshotRecorder` is NOT called per-directory (nothing is ever filtered)

### Step 4: Sequential Path -- `SequentialDirectoryVisitor`

Simple recursive method, runs entirely on the calling thread:

```java
private class SequentialDirectoryVisitor {
    final DirectoryWalkerPredicate predicate;  // non-null
    final Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder;

    SnapshotResult processDirectory(Path dir, List<String> relativePathSegments,
                                     Set<String> parentDirPaths, SymbolicLinkMapping mapping) {
        // List children, for each:
        //   - Check defaultExcludes + predicate (shouldVisit)
        //   - Directory: recurse immediately (no wrapper)
        //   - File: hash inline immediately (no wrapper)
        //   - Track isCurrentDirFiltered, filteredChildDirs
        // Build snapshot, report unfiltered children if filtered
    }
```

- No executor calls, no Futures, no wrapper objects
- Uses mutable `HashSet<String>` for parentDirPaths (efficient for sequential add/remove)
- Uses mutable `List<String>` for relativePathSegments (add before recurse, remove after)
- Calls `unfilteredSnapshotRecorder` for filtered directories' unfiltered children

### Step 5: Shared Code (methods on DirectorySnapshotter or static)

- `buildDirectorySnapshot(absolutePath, name, accessType, children)` -- sort + Merkle hash (unchanged)
- `snapshotFile(path, name, attrs, accessType)` -- check previouslyKnownSnapshots, hash file
- `getInternedFileName(path)` -- extract and intern filename
- `intern(string)` -- delegate to stringInterner
- `readAttributesOfSymlinkTarget(symlink, symlinkAttrs)` -- static
- `listDirectoryChildren(dir)` -- wraps `Files.list()` with error handling
- `DefaultExcludes` inner class -- reuse as-is
- `SymbolicLinkMapping` interface + `DefaultSymbolicLinkMapping` -- reuse as-is

---

## Caller Changes

### `ManagedForkJoinPool` (new)
- `platforms/core-runtime/concurrent/src/main/java/org/gradle/internal/concurrent/ManagedForkJoinPool.java` -- new interface
- `platforms/core-runtime/concurrent/src/main/java/org/gradle/internal/concurrent/ExecutorFactory.java` -- change return type of `createWorkStealingPool`
- `platforms/core-runtime/concurrent/src/main/java/org/gradle/internal/concurrent/DefaultExecutorFactory.java` -- new `TrackedManagedForkJoinPool` impl

### `DefaultFileSystemAccess.java`
- `platforms/core-execution/snapshots/src/main/java/org/gradle/internal/vfs/impl/DefaultFileSystemAccess.java`
- Change constructor: replace two `ExecutorService` params with single `ManagedForkJoinPool`
- Update `onDefaultExcludesChanged` to pass `forkJoinPool`

### `VirtualFileSystemServices.java`
- `subprojects/core/src/main/java/org/gradle/internal/service/scopes/VirtualFileSystemServices.java`
- `GradleUserHomeServices.createFileSystemAccess()` (line ~257): replace two executor creates with single `executorFactory.createWorkStealingPool("Directory snapshotter")`
- `BuildSessionServices.createFileSystemAccess()` (line ~365): same

**ForkJoinPool lifecycle:** Fully managed via `ManagedForkJoinPool` extending `ManagedExecutor` -- tracked by `DefaultExecutorFactory`, shut down on `stop()`.

### Test Files
- `DirectorySnapshotterTest.groovy` (line 55) -- replace two executors with `ManagedForkJoinPool` (or test stub)
- `DirectorySnapshotterStatisticsTest.groovy` (line 39) -- same
- `DirectorySnapshotterAsDirectoryWalkerTest.groovy` (line 81) -- same
- `TestFiles.java` (line ~221) -- update `fileSystemAccess()` factory
- `TestExecutorFactory.groovy` -- update `createWorkStealingPool()` to return `ManagedForkJoinPool`
- `ConcurrentTestUtil.groovy` -- update `ManagedExecutorStub` or add `ManagedForkJoinPool` stub

---

## Verification

1. Run `DirectorySnapshotterTest` -- all scenarios (filtered, unfiltered, symlinks, cycles, broken symlinks, empty dirs, excludes, reuse)
2. Run `DirectorySnapshotterStatisticsTest`
3. Run `DirectorySnapshotterAsDirectoryWalkerTest`
4. Run `DefaultSnapshotHierarchyTest`, `AbstractFileWatcherUpdaterTest`
5. Run `DefaultExcludesIntegrationTest`
6. Verify snapshot hashes are identical (the Merkle hash computation is unchanged)

## Key Correctness Invariants

- `buildDirectorySnapshot()` sorts children by `BY_NAME` before Merkle hashing -> deterministic regardless of child collection order
- `snapshotFile()` checks `previouslyKnownSnapshots` before hashing -> reuse preserved
- `StringInterner`, `FileHasher`, `Collector` are all thread-safe -> safe from concurrent ForkJoinPool threads
- `DirectoryWalkerPredicate` is NOT thread-safe -> only used in sequential path
- `PersistentSet<String>` for parentDirPaths in parallel path -> immutable CHAMP trie, `plus()` returns new set with structural sharing, safe sharing between forked tasks
