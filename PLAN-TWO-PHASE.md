# Plan: Rewrite DirectorySnapshotter -- Two-Phase Approach

## Context

The current parallel DirectorySnapshotter mixes traversal and hashing in the same tasks, causing GC pressure (Future/lambda/ArrayList per tiny directory) and suboptimal work distribution. This rewrite cleanly separates concerns into phases.

## Architecture: Three Phases

```
Phase 1 (parallel): Walk tree, collect skeleton
  - ForkJoinPool RecursiveTask per directory
  - ONLY does: list children, check previouslyKnownSnapshots, collect metadata
  - Returns lightweight DirectoryTreeInfo (no file I/O, no hashing)
  - Very fast: stat() calls + cache lookups
  - previouslyKnownSnapshots short-circuits entire cached subtrees

Phase 2 (parallel): Hash all uncached files
  - Flat list of files extracted from the skeleton
  - Embarrassingly parallel -- no tree structure, no blocking joins
  - Byte-based threshold decides inline vs parallel
  - Results stored directly on FileToHash objects (no Map lookup)

Phase 3 (sequential, in-memory): Build snapshots bottom-up
  - Walk the skeleton, attach hashed results, compute Merkle hashes
  - Pure computation, no I/O -- very fast
```

## Entry Point

```java
public FileSystemLocationSnapshot snapshot(String absolutePath, @Nullable predicate,
        previouslyKnownSnapshots, unfilteredSnapshotRecorder) {
    collector.recordVisitHierarchy();
    Path rootPath = Paths.get(absolutePath);
    BasicFileAttributes rootAttrs = readAttributes(rootPath, NOFOLLOW_LINKS);

    if (!rootAttrs.isDirectory()) {
        return snapshotFileRoot(rootPath, rootAttrs, predicate, ...);
    }

    ImmutableMap<String, ?> snapshots = ImmutableMap.copyOf(previouslyKnownSnapshots);

    // Phase 1: tree walk -> skeleton
    //   Parallel (ForkJoinPool) when no predicate
    //   Sequential (calling thread) when predicate present (not thread-safe)
    DirectoryTreeInfo skeleton;
    if (predicate == null) {
        skeleton = forkJoinPool.invoke(
            new TreeWalkTask(rootPath, EMPTY_MAPPING, PersistentSet.of(), snapshots));
    } else {
        skeleton = new SequentialTreeWalker(predicate, snapshots)
            .walk(rootPath, new ArrayList<>(), new HashSet<>(), EMPTY_MAPPING);
    }

    // Phase 2: parallel file hashing (same for both paths -- predicate not involved)
    List<FileToHash> allFiles = skeleton.collectAllFiles();
    if (!allFiles.isEmpty()) {
        hashFiles(allFiles);
    }

    // Phase 3: build snapshot tree (sequential, in-memory)
    DirectorySnapshot result = buildSnapshotFromSkeleton(skeleton, unfilteredSnapshotRecorder);
    if (!skeleton.hasFilteredChildren()) {
        unfilteredSnapshotRecorder.accept(result);
    }
    return result;
}
```

All three paths (parallel, sequential-with-predicate, file-root) share the same Phase 2 and Phase 3. Only Phase 1 differs.

---

## Data Structures

### `DirectoryTreeInfo` -- Phase 1 output, Phase 3 input

```java
private static class DirectoryTreeInfo {
    final String internedAbsPath;
    final String internedName;
    final AccessType accessType;

    // Already resolved (from previouslyKnownSnapshots cache)
    final List<FileSystemLocationSnapshot> cachedChildren;

    // Need Phase 2 hashing, then Phase 3 snapshot construction
    final List<FileToHash> filesToHash;

    // Uncached subdirectories (have their own filesToHash and subdirectories)
    final List<DirectoryTreeInfo> uncachedSubdirectories;

    // Filtering state (only set by sequential walker when predicate != null)
    boolean isFiltered;           // true if predicate rejected any child of THIS directory
    Set<DirectoryTreeInfo> filteredSubdirectories;  // subdirs that are themselves filtered

    // Collect all FileToHash recursively from this subtree
    void collectAllFiles(List<FileToHash> accumulator) {
        accumulator.addAll(filesToHash);
        for (DirectoryTreeInfo sub : uncachedSubdirectories) {
            sub.collectAllFiles(accumulator);
        }
    }

    boolean hasFilteredChildren() {
        if (isFiltered) return true;
        for (DirectoryTreeInfo sub : uncachedSubdirectories) {
            if (sub.hasFilteredChildren()) return true;
        }
        return false;
    }
}
```

When `predicate == null` (parallel path), `isFiltered` is always false and `filteredSubdirectories` is empty -- these fields have zero cost.

### `FileToHash` -- bridges Phase 1 → Phase 2 → Phase 3

```java
private static class FileToHash {
    final Path path;
    final String internedName;
    final String internedAbsPath;
    final BasicFileAttributes attrs;
    final AccessType accessType;

    // Written by Phase 2, read by Phase 3
    // Safe without volatile: ForkJoinPool.invoke() provides happens-before barrier
    FileSystemLeafSnapshot result;
}
```

No Map needed in Phase 3 -- each `DirectoryTreeInfo` already holds references to its own `FileToHash` objects with results populated.

---

## Phase 1: Parallel Tree Walk

### `TreeWalkTask extends RecursiveTask<DirectoryTreeInfo>`

```java
private class TreeWalkTask extends RecursiveTask<DirectoryTreeInfo> {
    final Path dir;
    final SymbolicLinkMapping mapping;
    final PersistentSet<String> parentDirPaths;  // CHAMP trie for cycle detection
    final ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots;

    @Override
    protected DirectoryTreeInfo compute() {
        String internedName = getInternedFileName(dir);
        String internedAbsPath = intern(mapping.remapAbsolutePath(dir));

        // Check cache -- return entire cached subtree
        FileSystemLocationSnapshot cached = previouslyKnownSnapshots.get(internedAbsPath);
        if (cached instanceof DirectorySnapshot) {
            // Return a "fully resolved" skeleton with just the cached snapshot
            return DirectoryTreeInfo.cached(internedAbsPath, internedName, (DirectorySnapshot) cached);
        }

        PersistentSet<String> currentPaths = parentDirPaths.plus(dir.toString());

        List<FileSystemLocationSnapshot> cachedChildren = new ArrayList<>();
        List<FileToHash> filesToHash = new ArrayList<>();
        List<TreeWalkTask> forkedSubdirTasks = new ArrayList<>();

        try (Stream<Path> children = listDirectoryChildren(dir)) {
            collector.recordVisitDirectory();
            children.forEach(child -> {
                BasicFileAttributes attrs = readAttributes(child, NOFOLLOW_LINKS);
                String childName = getInternedFileName(child);

                if (attrs.isDirectory()) {
                    if (!defaultExcludes.excludeDir(childName)) {
                        TreeWalkTask task = new TreeWalkTask(child, mapping, currentPaths, previouslyKnownSnapshots);
                        task.fork();
                        forkedSubdirTasks.add(task);
                    }
                } else if (attrs.isSymbolicLink()) {
                    collector.recordVisitFile();
                    handleSymlink(child, childName, attrs, currentPaths,
                        cachedChildren, filesToHash, forkedSubdirTasks);
                } else {
                    collector.recordVisitFile();
                    if (!defaultExcludes.excludeFile(childName)) {
                        // Check file-level cache
                        String fileAbsPath = intern(mapping.remapAbsolutePath(child));
                        FileSystemLocationSnapshot cachedFile = previouslyKnownSnapshots.get(fileAbsPath);
                        if (cachedFile instanceof FileSystemLeafSnapshot) {
                            cachedChildren.add(cachedFile);
                        } else {
                            filesToHash.add(new FileToHash(child, childName, fileAbsPath, attrs, AccessType.DIRECT));
                        }
                    }
                }
            });
        }

        // Join forked subdirectory tasks
        List<DirectoryTreeInfo> uncachedSubdirs = new ArrayList<>();
        for (TreeWalkTask task : forkedSubdirTasks) {
            DirectoryTreeInfo subdirInfo = task.join();
            if (subdirInfo.isFullyCached()) {
                cachedChildren.add(subdirInfo.getCachedSnapshot());
            } else {
                uncachedSubdirs.add(subdirInfo);
            }
        }

        return new DirectoryTreeInfo(internedAbsPath, internedName, AccessType.DIRECT,
            cachedChildren, filesToHash, uncachedSubdirs);
    }
}
```

**Key properties:**
- Tasks are homogeneous: ONLY directory listing + stat + cache lookup
- No file I/O (no hashing)
- Very lightweight -- tasks complete fast, especially for cached subtrees
- `PersistentSet<String>` for cycle detection (~O(1) via structural sharing)
- File-level cache check happens here (avoids adding cached files to filesToHash)

### Symlink Handling in Phase 1

```java
private void handleSymlink(Path child, String childName, BasicFileAttributes attrs,
        PersistentSet<String> currentPaths,
        List<FileSystemLocationSnapshot> cachedChildren,
        List<FileToHash> filesToHash,
        List<TreeWalkTask> forkedSubdirTasks) {
    BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(child, attrs);
    if (targetAttrs.isDirectory()) {
        // Symlink to directory: fork a TreeWalkTask for the target
        try {
            Path targetDir = child.toRealPath();
            if (!currentPaths.contains(targetDir.toString())) {
                SymbolicLinkMapping newMapping = mapping.withNewMapping(
                    child.toString(), targetDir.toString(), Collections.emptyList());
                TreeWalkTask task = new TreeWalkTask(
                    targetDir, newMapping, currentPaths, previouslyKnownSnapshots);
                task.fork();
                forkedSubdirTasks.add(task);
                // Phase 3 will wrap this with VIA_SYMLINK access type
            }
        } catch (IOException e) {
            throw new UncheckedIOException(...);
        }
    } else {
        // Symlink to file
        if (!defaultExcludes.excludeFile(childName)) {
            String fileAbsPath = intern(mapping.remapAbsolutePath(child));
            FileSystemLocationSnapshot cachedFile = previouslyKnownSnapshots.get(fileAbsPath);
            if (cachedFile instanceof FileSystemLeafSnapshot) {
                cachedChildren.add(cachedFile);
            } else {
                filesToHash.add(new FileToHash(child, childName, fileAbsPath, targetAttrs, AccessType.VIA_SYMLINK));
            }
        }
    }
}
```

---

## Phase 2: Parallel File Hashing

After Phase 1 returns the skeleton, collect all files and hash them:

```java
private void hashFiles(List<FileToHash> allFiles) {
    long totalBytes = 0;
    for (FileToHash f : allFiles) totalBytes += f.attrs.size();

    if (totalBytes <= PARALLEL_BYTES_THRESHOLD || allFiles.size() <= 1) {
        // Inline: small total work
        for (FileToHash f : allFiles) {
            f.result = snapshotFile(f.path, f.internedName, f.internedAbsPath, f.attrs, f.accessType);
        }
    } else {
        // Parallel: fork chunks via ForkJoinPool
        forkJoinPool.invoke(new HashAllFilesTask(allFiles));
    }
}
```

### `HashAllFilesTask extends RecursiveAction`

```java
private class HashAllFilesTask extends RecursiveAction {
    final List<FileToHash> files;
    final int start, end;

    @Override
    protected void compute() {
        int count = end - start;
        if (count <= CHUNK_SIZE) {
            // Base case: hash this chunk inline
            for (int i = start; i < end; i++) {
                FileToHash f = files.get(i);
                f.result = snapshotFile(f.path, f.internedName, f.internedAbsPath, f.attrs, f.accessType);
            }
        } else {
            // Split in half, fork left, compute right inline
            int mid = start + count / 2;
            HashAllFilesTask left = new HashAllFilesTask(files, start, mid);
            left.fork();
            new HashAllFilesTask(files, mid, end).compute();  // inline right half
            left.join();
        }
    }
}
```

**Byte-based threshold (same as before):**

| Scenario | Total bytes | Decision |
|---|---|---|
| 5 jars (10MB each) | 50MB | parallel |
| 5 class files (5KB each) | 25KB | inline |
| 2000 class files (5KB each) | 10MB | parallel |
| 1 huge jar (100MB) | 100MB | inline (single file) |

**Chunk size calculation:**
```java
private static final long PARALLEL_BYTES_THRESHOLD = 256 * 1024; // 256KB

// For RecursiveAction split: stop splitting when chunk is small enough
int CHUNK_SIZE = Math.max(2, allFiles.size() / (Runtime.getRuntime().availableProcessors() * 2));
```

**Optional optimization:** Sort files by size descending before Phase 2. Large files (jars) get hashed first, filling the pipeline. Small files fill gaps via work-stealing.

---

## Phase 3: Build Snapshot Tree

Sequential, in-memory, recursive. Very fast. Handles `unfilteredSnapshotRecorder` for filtered directories.

```java
private DirectorySnapshot buildSnapshotFromSkeleton(DirectoryTreeInfo info,
        Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder) {
    List<FileSystemLocationSnapshot> children = new ArrayList<>();

    // Add cached children (from previouslyKnownSnapshots)
    children.addAll(info.cachedChildren);

    // Add hashed files (results populated by Phase 2)
    for (FileToHash f : info.filesToHash) {
        children.add(f.result);
    }

    // Recursively build uncached subdirectories
    for (DirectoryTreeInfo subdir : info.uncachedSubdirectories) {
        children.add(buildSnapshotFromSkeleton(subdir, unfilteredSnapshotRecorder));
    }

    DirectorySnapshot result = buildDirectorySnapshot(
        info.internedAbsPath, info.internedName, info.accessType, children);

    // For filtered directories: report unfiltered children to recorder
    // (skip child dirs that are themselves filtered -- their children were already reported)
    if (info.isFiltered) {
        for (FileSystemLocationSnapshot child : result.getChildren()) {
            if (child.getType() != FileType.Directory
                    || !info.filteredSubdirectories.contains(child)) {
                unfilteredSnapshotRecorder.accept(child);
            }
        }
    }

    return result;
}
```

`buildDirectorySnapshot()` is unchanged: sort by `BY_NAME`, compute Merkle hash.

**When predicate is null:** `isFiltered` is always false on every node, so the `if (info.isFiltered)` block never executes -- zero overhead.

---

## Phase 1 (Sequential): `SequentialTreeWalker` (predicate != null)

Same skeleton output as the parallel `TreeWalkTask`, but runs on the calling thread. Adds predicate filtering and relative path tracking.

```java
private class SequentialTreeWalker {
    final DirectoryWalkerPredicate predicate;
    final ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots;

    DirectoryTreeInfo walk(Path dir, List<String> relativePathSegments,
                           Set<String> parentDirPaths, SymbolicLinkMapping mapping) {
        String internedName = getInternedFileName(dir);
        String internedAbsPath = intern(mapping.remapAbsolutePath(dir));

        // No directory-level cache reuse when predicate is present (existing behavior)
        // File-level reuse still works via previouslyKnownSnapshots

        parentDirPaths.add(dir.toString());

        List<FileSystemLocationSnapshot> cachedChildren = new ArrayList<>();
        List<FileToHash> filesToHash = new ArrayList<>();
        List<DirectoryTreeInfo> uncachedSubdirs = new ArrayList<>();
        boolean isFiltered = false;
        Set<DirectoryTreeInfo> filteredSubdirs = new HashSet<>();

        try (Stream<Path> children = listDirectoryChildren(dir)) {
            collector.recordVisitDirectory();
            children.forEach(child -> {
                BasicFileAttributes attrs = readAttributes(child, NOFOLLOW_LINKS);
                String childName = getInternedFileName(child);

                if (attrs.isDirectory()) {
                    Iterable<String> segments = concat(relativePathSegments, singleton(childName));
                    if (shouldVisit(child, childName, true, segments)) {
                        relativePathSegments.add(childName);
                        DirectoryTreeInfo subdirInfo = walk(child, relativePathSegments, parentDirPaths, mapping);
                        uncachedSubdirs.add(subdirInfo);
                        if (subdirInfo.hasFilteredChildren()) {
                            filteredSubdirs.add(subdirInfo);
                            isFiltered = true;
                        }
                        relativePathSegments.remove(relativePathSegments.size() - 1);
                    } else {
                        isFiltered = true;  // predicate rejected this dir
                    }
                } else if (attrs.isSymbolicLink()) {
                    collector.recordVisitFile();
                    // Handle symlinks with predicate checks + path segments
                    // (similar to parallel but sequential, with shouldVisit checks)
                    ...
                } else {
                    collector.recordVisitFile();
                    Iterable<String> segments = concat(relativePathSegments, singleton(childName));
                    if (shouldVisit(child, childName, false, segments)) {
                        String fileAbsPath = intern(mapping.remapAbsolutePath(child));
                        FileSystemLocationSnapshot cachedFile = previouslyKnownSnapshots.get(fileAbsPath);
                        if (cachedFile instanceof FileSystemLeafSnapshot) {
                            cachedChildren.add(cachedFile);
                        } else {
                            filesToHash.add(new FileToHash(child, childName, fileAbsPath, attrs, AccessType.DIRECT));
                        }
                    } else {
                        isFiltered = true;  // predicate rejected this file
                    }
                }
            });
        }

        parentDirPaths.remove(dir.toString());

        DirectoryTreeInfo info = new DirectoryTreeInfo(internedAbsPath, internedName, AccessType.DIRECT,
            cachedChildren, filesToHash, uncachedSubdirs);
        info.isFiltered = isFiltered;
        info.filteredSubdirectories = filteredSubdirs;
        return info;
    }

    private boolean shouldVisit(Path path, String name, boolean isDir, Iterable<String> segments) {
        if (isDir ? defaultExcludes.excludeDir(name) : defaultExcludes.excludeFile(name)) {
            return false;
        }
        boolean allowed = predicate.test(path, name, isDir, mapping.getRemappedSegments(segments));
        return allowed;
        // Note: filtering flag is set by the caller based on return value
    }
}
```

**Key:** Same `DirectoryTreeInfo` output as the parallel path. Phase 2 and Phase 3 are shared.

---

## Class Structure

```
DirectorySnapshotter (rewritten)
  |
  +-- Fields: hasher, stringInterner, defaultExcludes, collector, forkJoinPool
  |
  +-- snapshot(...)                        // entry point
  +-- hashFiles(List<FileToHash>)          // Phase 2 (shared by both paths)
  +-- buildSnapshotFromSkeleton(...)       // Phase 3 (shared by both paths)
  |
  +-- TreeWalkTask                         // Phase 1 parallel: RecursiveTask<DirectoryTreeInfo>
  +-- SequentialTreeWalker                 // Phase 1 sequential: builds same DirectoryTreeInfo
  +-- HashAllFilesTask                     // Phase 2: RecursiveAction (split-in-half)
  |
  +-- DirectoryTreeInfo                    // Skeleton node (bridges Phase 1 -> Phase 3)
  +-- FileToHash                           // File descriptor (bridges Phase 1 -> Phase 2 -> Phase 3)
  |
  +-- SymbolicLinkMapping (reuse)
  +-- DefaultExcludes (reuse)
```

Phase 2 and Phase 3 are completely shared -- only Phase 1 has two implementations (parallel vs sequential).

---

## Infrastructure Changes

### `ManagedForkJoinPool` (new)
- `platforms/core-runtime/concurrent/.../ManagedForkJoinPool.java` -- new interface extending `ManagedExecutor` with `<T> T invoke(ForkJoinTask<T>)`
- `platforms/core-runtime/concurrent/.../ExecutorFactory.java` -- `createWorkStealingPool` returns `ManagedForkJoinPool` (covariant)
- `platforms/core-runtime/concurrent/.../DefaultExecutorFactory.java` -- new `TrackedManagedForkJoinPool` impl wrapping ForkJoinPool

### Caller Updates
- `DefaultFileSystemAccess.java` -- single `ManagedForkJoinPool` instead of two `ExecutorService`
- `VirtualFileSystemServices.java` -- single `executorFactory.createWorkStealingPool("Directory snapshotter")`
- Test files -- update constructor calls

---

## Why This Is Better

1. **Homogeneous tasks**: Phase 1 tasks only do listing + stat. Phase 2 tasks only do hashing. No mixed concerns.

2. **Optimal hashing distribution**: Flat file list means all files across ALL directories compete for ForkJoinPool threads equally. No per-directory chunking overhead. A 50MB jar from one directory and 2000 small classes from another are distributed optimally.

3. **Minimal allocations on hot path**: Phase 1 (most of the work for incremental builds) creates only `DirectoryTreeInfo` and `FileToHash` -- no Futures, no lambdas, no HashSets.

4. **Clean testability**: Each phase can be tested independently.

5. **Incremental build fast path**: When `previouslyKnownSnapshots` covers most of the tree, Phase 1 returns mostly-cached skeletons, Phase 2 has few/zero files, Phase 3 is trivial.

---

## Verification

1. Run `DirectorySnapshotterTest` -- all scenarios
2. Run `DirectorySnapshotterStatisticsTest`
3. Run `DirectorySnapshotterAsDirectoryWalkerTest`
4. Run `DefaultSnapshotHierarchyTest`, `AbstractFileWatcherUpdaterTest`
5. Run `DefaultExcludesIntegrationTest`
6. Verify snapshot hashes identical (Merkle hash computation unchanged)

## Correctness Invariants

- `buildDirectorySnapshot()` sorts children by `BY_NAME` -> deterministic
- File-level `previouslyKnownSnapshots` checked in Phase 1 -> reuse preserved
- Directory-level `previouslyKnownSnapshots` checked in Phase 1 -> subtree skip preserved
- `StringInterner`, `FileHasher`, `Collector` all thread-safe
- `DirectoryWalkerPredicate` NOT thread-safe -> only in sequential path
- `PersistentSet<String>` for cycle detection -> immutable, safe sharing
- Phase 2 results visible to Phase 3 via `ForkJoinPool.invoke()` happens-before barrier
