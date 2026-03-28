/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.snapshot.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Iterables;
import org.gradle.internal.collect.PersistentSet;
import org.gradle.internal.concurrent.ManagedForkJoinPool;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLeafSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.jspecify.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.DirectoryStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Snapshots directories using a three-phase approach:
 * <ol>
 *   <li><b>Phase 1 - Tree Walk:</b> Parallel (or sequential when filtered) traversal that produces
 *       a lightweight {@link DirectoryTreeInfo} skeleton. No file hashing occurs.</li>
 *   <li><b>Phase 2 - File Hashing:</b> All uncached files are hashed in parallel using the ForkJoinPool.
 *       Uses a byte-based threshold to decide inline vs parallel hashing.</li>
 *   <li><b>Phase 3 - Snapshot Build:</b> Sequential in-memory construction of the final
 *       {@link DirectorySnapshot} tree from the skeleton and hashed results.</li>
 * </ol>
 */
public class DirectorySnapshotter {
    private static final HashCode DIR_SIGNATURE = Hashing.signature("DIR");

    @VisibleForTesting
    static final long PARALLEL_BYTES_THRESHOLD = 256 * 1024;

    private static final SymbolicLinkMapping EMPTY_SYMBOLIC_LINK_MAPPING = new SymbolicLinkMapping() {
        @Override
        public String remapAbsolutePath(Path path) {
            return path.toString();
        }

        @Override
        public SymbolicLinkMapping withNewMapping(String source, String target, Iterable<String> relativePathSegments) {
            return new DefaultSymbolicLinkMapping(source, target, relativePathSegments);
        }

        @Override
        public Iterable<String> getRemappedSegments(Iterable<String> segments) {
            return segments;
        }
    };

    private final FileHasher hasher;
    private final Interner<String> stringInterner;
    private final DefaultExcludes defaultExcludes;
    private final DirectorySnapshotterStatistics.Collector collector;
    private final ManagedForkJoinPool forkJoinPool;

    public DirectorySnapshotter(
        FileHasher hasher,
        Interner<String> stringInterner,
        Collection<String> defaultExcludes,
        DirectorySnapshotterStatistics.Collector collector,
        ManagedForkJoinPool forkJoinPool
    ) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.defaultExcludes = new DefaultExcludes(defaultExcludes);
        this.collector = collector;
        this.forkJoinPool = forkJoinPool;
    }

    public FileSystemLocationSnapshot snapshot(
        String absolutePath,
        SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate,
        Map<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots,
        Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder
    ) {
        collector.recordVisitHierarchy();
        Path rootPath = Paths.get(absolutePath);

        BasicFileAttributes rootAttrs;
        try {
            rootAttrs = Files.readAttributes(rootPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            collector.recordVisitFileFailed();
            throw new UncheckedIOException(e);
        }

        if (!rootAttrs.isDirectory()) {
            collector.recordVisitFile();
            String rootName = getInternedFileName(rootPath);
            return snapshotFileRoot(rootPath, rootName, rootAttrs, predicate, previouslyKnownSnapshots, unfilteredSnapshotRecorder);
        }

        ImmutableMap<String, FileSystemLocationSnapshot> snapshots = ImmutableMap.copyOf(previouslyKnownSnapshots);

        DirectoryTreeInfo skeleton = walkDirectory(rootPath, predicate, snapshots, EMPTY_SYMBOLIC_LINK_MAPPING);
        DirectorySnapshot result = hashAndBuildSnapshot(skeleton, unfilteredSnapshotRecorder);
        if (!skeleton.hasFilteredDescendants()) {
            unfilteredSnapshotRecorder.accept(result);
        }
        return result;
    }

    // ---- Phase 1: Tree Walk dispatch ----

    private DirectoryTreeInfo walkDirectory(
        Path dir,
        SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate,
        ImmutableMap<String, ? extends FileSystemLocationSnapshot> snapshots,
        SymbolicLinkMapping mapping
    ) {
        if (predicate == null) {
            return forkJoinPool.invoke(new TreeWalker(dir, mapping, PersistentSet.of(), snapshots, null, null));
        } else {
            return new TreeWalker(dir, mapping, PersistentSet.of(), snapshots, predicate, new ArrayList<>()).compute();
        }
    }

    // ---- Phase 2: File Hashing ----

    private void hashFiles(List<FileToHash> allFiles) {
        long totalBytes = 0;
        for (FileToHash f : allFiles) {
            totalBytes += f.attrs.size();
        }

        if (totalBytes <= PARALLEL_BYTES_THRESHOLD || allFiles.size() <= 1) {
            for (FileToHash f : allFiles) {
                f.result = snapshotFile(f);
            }
        } else {
            forkJoinPool.invoke(new HashAllFilesTask(allFiles, 0, allFiles.size()));
        }
    }

    // ---- Phase 2 + 3: Hash files then build snapshot tree ----

    private DirectorySnapshot hashAndBuildSnapshot(
        DirectoryTreeInfo skeleton,
        Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder
    ) {
        List<FileToHash> allFiles = new ArrayList<>();
        skeleton.collectAllFiles(allFiles);
        if (!allFiles.isEmpty()) {
            hashFiles(allFiles);
        }
        return buildSnapshotFromSkeleton(skeleton, unfilteredSnapshotRecorder);
    }

    // ---- Phase 3: Snapshot Build ----

    private DirectorySnapshot buildSnapshotFromSkeleton(
        DirectoryTreeInfo info,
        Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder
    ) {
        List<FileSystemLocationSnapshot> children = new ArrayList<>();
        Set<FileSystemLocationSnapshot> filteredChildSnapshots = new HashSet<>();

        children.addAll(info.cachedChildren);

        for (FileToHash f : info.filesToHash) {
            children.add(f.result);
        }

        for (DirectoryTreeInfo subdir : info.uncachedSubdirectories) {
            DirectorySnapshot childSnapshot = buildSnapshotFromSkeleton(subdir, unfilteredSnapshotRecorder);
            children.add(childSnapshot);
            if (info.filteredSubdirectories.contains(subdir)) {
                filteredChildSnapshots.add(childSnapshot);
            }
        }

        DirectorySnapshot result = buildDirectorySnapshot(info.internedAbsPath, info.internedName, info.accessType, children);

        if (info.isFiltered) {
            for (FileSystemLocationSnapshot child : result.getChildren()) {
                if (child.getType() != FileType.Directory || !filteredChildSnapshots.contains(child)) {
                    unfilteredSnapshotRecorder.accept(child);
                }
            }
        }

        return result;
    }

    // ---- Root file handling ----

    private FileSystemLocationSnapshot snapshotFileRoot(
        Path file,
        String internedName,
        BasicFileAttributes attrs,
        SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate,
        Map<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots,
        Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder
    ) {
        ImmutableMap<String, FileSystemLocationSnapshot> snapshots = ImmutableMap.copyOf(previouslyKnownSnapshots);
        if (attrs.isSymbolicLink()) {
            BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(file, attrs);
            if (targetAttrs.isDirectory()) {
                // Symlink to directory at root
                try {
                    Path targetDir = file.toRealPath();
                    SymbolicLinkMapping newMapping = EMPTY_SYMBOLIC_LINK_MAPPING.withNewMapping(file.toString(), targetDir.toString(), Collections.emptyList());

                    collector.recordVisitHierarchy();
                    DirectoryTreeInfo skeleton = walkDirectory(targetDir, predicate, snapshots, newMapping);
                    DirectorySnapshot targetSnapshot = hashAndBuildSnapshot(skeleton, unfilteredSnapshotRecorder);

                    DirectorySnapshot snapshotViaSymlink = new DirectorySnapshot(targetSnapshot.getAbsolutePath(), internedName,
                        AccessType.VIA_SYMLINK, targetSnapshot.getHash(), targetSnapshot.getChildren());

                    if (!skeleton.hasFilteredDescendants()) {
                        unfilteredSnapshotRecorder.accept(snapshotViaSymlink);
                    }
                    return snapshotViaSymlink;
                } catch (IOException e) {
                    throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
                }
            } else {
                FileSystemLeafSnapshot result = snapshotLeafFile(file, internedName, targetAttrs, AccessType.VIA_SYMLINK, EMPTY_SYMBOLIC_LINK_MAPPING, snapshots);
                unfilteredSnapshotRecorder.accept(result);
                return result;
            }
        } else {
            FileSystemLeafSnapshot result = snapshotLeafFile(file, internedName, attrs, AccessType.DIRECT, EMPTY_SYMBOLIC_LINK_MAPPING, snapshots);
            unfilteredSnapshotRecorder.accept(result);
            return result;
        }
    }

    // ---- Shared utilities ----

    private static DirectorySnapshot buildDirectorySnapshot(String absolutePath, String name, AccessType accessType, List<FileSystemLocationSnapshot> children) {
        children.sort(FileSystemLocationSnapshot.BY_NAME);
        Hasher hasher = Hashing.newHasher();
        hasher.putHash(DIR_SIGNATURE);
        for (FileSystemLocationSnapshot child : children) {
            hasher.putString(child.getName());
            hasher.putHash(child.getHash());
        }
        return new DirectorySnapshot(absolutePath, name, accessType, hasher.hash(), children);
    }

    private FileSystemLeafSnapshot snapshotFile(FileToHash fileToHash) {
        return snapshotLeafFile(fileToHash.path, fileToHash.internedName, fileToHash.attrs, fileToHash.accessType,
            fileToHash.mapping, fileToHash.previouslyKnownSnapshots);
    }

    private FileSystemLeafSnapshot snapshotLeafFile(
        Path absoluteFilePath,
        String internedName,
        BasicFileAttributes attrs,
        AccessType accessType,
        SymbolicLinkMapping mapping,
        ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots
    ) {
        String internedAbsPath = intern(mapping.remapAbsolutePath(absoluteFilePath));
        FileSystemLocationSnapshot cached = previouslyKnownSnapshots.get(internedAbsPath);
        if (cached != null) {
            if (!(cached instanceof FileSystemLeafSnapshot)) {
                throw new IllegalStateException("Expected a previously known leaf snapshot at " + internedAbsPath + ", but found " + cached);
            }
            return (FileSystemLeafSnapshot) cached;
        }
        if (attrs.isSymbolicLink()) {
            return new MissingFileSnapshot(internedAbsPath, internedName, accessType);
        } else if (!attrs.isRegularFile()) {
            throw new UncheckedIOException(new IOException(String.format("Cannot snapshot %s: not a regular file", internedAbsPath)));
        }
        long lastModified = attrs.lastModifiedTime().toMillis();
        long fileLength = attrs.size();
        FileMetadata metadata = DefaultFileMetadata.file(lastModified, fileLength, accessType);
        HashCode hash = hasher.hash(absoluteFilePath.toFile(), fileLength, lastModified);
        return new RegularFileSnapshot(internedAbsPath, internedName, hash, metadata);
    }

    @SuppressWarnings("StreamResourceLeak") // Caller is responsible for closing via try-with-resources
    private DirectoryStream<Path> listDirectoryChildren(Path dir) {
        try {
            return Files.newDirectoryStream(dir);
        } catch (IOException e) {
            collector.recordVisitFileFailed();
            throw new UncheckedIOException(e);
        }
    }

    private static BasicFileAttributes readAttributesOfSymlinkTarget(Path symlink, BasicFileAttributes symlinkAttributes) {
        try {
            return Files.readAttributes(symlink, BasicFileAttributes.class);
        } catch (IOException ioe) {
            return symlinkAttributes;
        }
    }

    private static boolean isNotFileSystemLoopException(@Nullable IOException e) {
        return e != null && !(e instanceof FileSystemLoopException);
    }

    String getInternedFileName(Path path) {
        String absolutePath = path.toString();
        int lastSep = absolutePath.lastIndexOf(File.separatorChar);
        return intern(lastSep < 0 ? absolutePath : absolutePath.substring(lastSep + 1));
    }

    private String intern(String string) {
        return stringInterner.intern(string);
    }

    // ---- Phase 1: Unified Tree Walker ----
    //
    // Handles both parallel (predicate == null) and sequential/filtered (predicate != null) tree walking.
    // In parallel mode, subdirectories are forked as ForkJoinPool tasks and joined after iteration.
    // In sequential mode, subdirectories are walked inline via direct compute() calls.
    // The DeferredSubdir abstraction unifies the post-loop result collection for both modes.

    private class TreeWalker extends RecursiveTask<DirectoryTreeInfo> {
        private final Path dir;
        private final SymbolicLinkMapping mapping;
        private final PersistentSet<String> parentDirPaths;
        private final ImmutableMap<String, ? extends FileSystemLocationSnapshot> snapshots;
        private final SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate;
        @Nullable private final List<String> relativePathSegments;

        // Per-directory accumulation state, initialized in compute()
        private PersistentSet<String> currentPaths;
        private List<FileSystemLocationSnapshot> cachedChildren;
        private List<FileToHash> filesToHash;
        private List<DeferredSubdir> deferredSubdirs;
        private boolean isFiltered;

        TreeWalker(
            Path dir,
            SymbolicLinkMapping mapping,
            PersistentSet<String> parentDirPaths,
            ImmutableMap<String, ? extends FileSystemLocationSnapshot> snapshots,
            SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate,
            @Nullable List<String> relativePathSegments
        ) {
            this.dir = dir;
            this.mapping = mapping;
            this.parentDirPaths = parentDirPaths;
            this.snapshots = snapshots;
            this.predicate = predicate;
            this.relativePathSegments = relativePathSegments;
        }

        private boolean isParallel() {
            return predicate == null;
        }

        @Override
        protected DirectoryTreeInfo compute() {
            String internedName = getInternedFileName(dir);
            String internedAbsPath = intern(mapping.remapAbsolutePath(dir));

            // Directory-level cache reuse (only without predicate — filtered walks can't use it)
            if (isParallel()) {
                FileSystemLocationSnapshot cached = snapshots.get(internedAbsPath);
                if (cached instanceof DirectorySnapshot) {
                    return DirectoryTreeInfo.fullyCached(internedAbsPath, internedName, AccessType.DIRECT, (DirectorySnapshot) cached);
                } else if (cached != null) {
                    throw new IllegalStateException("Expected a previously known directory snapshot at " + internedAbsPath + " but got " + cached);
                }
            }

            currentPaths = parentDirPaths.plus(dir.toString());
            cachedChildren = new ArrayList<>();
            filesToHash = new ArrayList<>();
            deferredSubdirs = new ArrayList<>();
            isFiltered = false;

            try (DirectoryStream<Path> children = listDirectoryChildren(dir)) {
                collector.recordVisitDirectory();
                for (Path child : children) {
                    processChild(child);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            // Collect deferred subdirectory results
            List<DirectoryTreeInfo> uncachedSubdirs = new ArrayList<>();
            Set<DirectoryTreeInfo> filteredSubdirs = isParallel() ? Collections.emptySet() : new HashSet<>();
            for (DeferredSubdir deferred : deferredSubdirs) {
                DirectoryTreeInfo subdirInfo = deferred.resolve();
                if (deferred.symlinkName != null) {
                    subdirInfo.applySymlinkOverrides(deferred.symlinkName);
                }
                if (subdirInfo.cachedSnapshot != null) {
                    cachedChildren.add(subdirInfo.cachedSnapshot);
                } else {
                    uncachedSubdirs.add(subdirInfo);
                    if (!isParallel() && subdirInfo.hasFilteredDescendants()) {
                        filteredSubdirs.add(subdirInfo);
                        isFiltered = true;
                    }
                }
            }

            DirectoryTreeInfo info = new DirectoryTreeInfo(internedAbsPath, internedName, AccessType.DIRECT, cachedChildren, filesToHash, uncachedSubdirs);
            info.isFiltered = isFiltered;
            info.filteredSubdirectories = filteredSubdirs;
            return info;
        }

        private void processChild(Path child) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                String childName = getInternedFileName(child);
                if (attrs.isDirectory()) {
                    processDirectory(child, childName);
                } else if (attrs.isSymbolicLink()) {
                    processSymlink(child, childName, attrs);
                } else {
                    processFile(child, childName, attrs);
                }
            } catch (IOException e) {
                collector.recordVisitFileFailed();
                if (isNotFileSystemLoopException(e)) {
                    if (isParallel()) {
                        throw new UncheckedIOException(e);
                    }
                    String failedName = getInternedFileName(child);
                    boolean isDirectory = Files.isDirectory(child);
                    if (shouldVisit(child, failedName, isDirectory)) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }

        private void processDirectory(Path child, String childName) {
            if (shouldVisitDir(child, childName)) {
                deferredSubdirs.add(scheduleSubdirWalk(child, childName));
            } else if (!isParallel()) {
                isFiltered = true;
            }
        }

        private void processSymlink(Path child, String childName, BasicFileAttributes attrs) {
            collector.recordVisitFile();
            BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(child, attrs);
            if (targetAttrs.isDirectory()) {
                if (shouldVisitDir(child, childName)) {
                    DeferredSubdir symlinkSubdir = scheduleSymlinkDirWalk(child, childName);
                    if (symlinkSubdir != null) {
                        deferredSubdirs.add(symlinkSubdir);
                    }
                } else if (!isParallel()) {
                    isFiltered = true;
                }
            } else {
                if (shouldVisitFile(child, childName)) {
                    addFileOrCached(child, childName, targetAttrs, AccessType.VIA_SYMLINK);
                } else if (!isParallel()) {
                    isFiltered = true;
                }
            }
        }

        private void processFile(Path child, String childName, BasicFileAttributes attrs) {
            collector.recordVisitFile();
            if (shouldVisitFile(child, childName)) {
                addFileOrCached(child, childName, attrs, AccessType.DIRECT);
            } else if (!isParallel()) {
                isFiltered = true;
            }
        }

        private DeferredSubdir scheduleSubdirWalk(Path child, String childName) {
            if (isParallel()) {
                TreeWalker task = new TreeWalker(child, mapping, currentPaths, snapshots, null, null);
                task.fork();
                return new DeferredSubdir(task, null);
            } else {
                relativePathSegments.add(childName);
                DirectoryTreeInfo result = new TreeWalker(child, mapping, currentPaths, snapshots, predicate, relativePathSegments).compute();
                relativePathSegments.remove(relativePathSegments.size() - 1);
                return new DeferredSubdir(result, null);
            }
        }

        @Nullable
        private DeferredSubdir scheduleSymlinkDirWalk(Path symlinkPath, String childName) {
            try {
                Path targetDir = symlinkPath.toRealPath();
                String targetDirString = targetDir.toString();
                if (currentPaths.contains(targetDirString)) {
                    return null;
                }

                Iterable<String> symlinkSegments = isParallel()
                    ? Collections.emptyList()
                    : Iterables.concat(relativePathSegments, Collections.singleton(childName));
                SymbolicLinkMapping newMapping = mapping.withNewMapping(
                    symlinkPath.toString(), targetDirString, symlinkSegments);
                collector.recordVisitHierarchy();

                if (isParallel()) {
                    TreeWalker task = new TreeWalker(targetDir, newMapping, currentPaths, snapshots, null, null);
                    task.fork();
                    return new DeferredSubdir(task, childName);
                } else {
                    DirectoryTreeInfo result = new TreeWalker(targetDir, newMapping, currentPaths, snapshots, predicate, new ArrayList<>()).compute();
                    return new DeferredSubdir(result, childName);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", symlinkPath), e);
            }
        }

        private void addFileOrCached(Path child, String childName, BasicFileAttributes attrs, AccessType accessType) {
            String fileAbsPath = intern(mapping.remapAbsolutePath(child));
            FileSystemLocationSnapshot cachedFile = snapshots.get(fileAbsPath);
            if (cachedFile instanceof FileSystemLeafSnapshot) {
                cachedChildren.add(cachedFile);
            } else {
                filesToHash.add(new FileToHash(child, childName, attrs, accessType, mapping, snapshots));
            }
        }

        private boolean shouldVisitDir(Path child, String childName) {
            if (defaultExcludes.excludeDir(childName)) {
                return false;
            }
            if (predicate != null) {
                Iterable<String> segments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                return predicate.test(child, childName, true, mapping.getRemappedSegments(segments));
            }
            return true;
        }

        private boolean shouldVisitFile(Path child, String childName) {
            if (defaultExcludes.excludeFile(childName)) {
                return false;
            }
            if (predicate != null) {
                Iterable<String> segments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                return predicate.test(child, childName, false, mapping.getRemappedSegments(segments));
            }
            return true;
        }

        private boolean shouldVisit(Path child, String childName, boolean isDirectory) {
            return isDirectory ? shouldVisitDir(child, childName) : shouldVisitFile(child, childName);
        }
    }

    // ---- Phase 2: HashAllFilesTask ----

    private class HashAllFilesTask extends RecursiveAction {
        private final List<FileToHash> files;
        private final int start;
        private final int end;

        HashAllFilesTask(List<FileToHash> files, int start, int end) {
            this.files = files;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            int count = end - start;
            int chunkSize = Math.max(2, files.size() / (Runtime.getRuntime().availableProcessors() * 2));
            if (count <= chunkSize) {
                for (int i = start; i < end; i++) {
                    FileToHash f = files.get(i);
                    f.result = snapshotFile(f);
                }
            } else {
                int mid = start + count / 2;
                HashAllFilesTask left = new HashAllFilesTask(files, start, mid);
                left.fork();
                new HashAllFilesTask(files, mid, end).compute();
                left.join();
            }
        }
    }

    // ---- Data structures ----

    /**
     * Wraps either a forked {@link TreeWalker} task (parallel mode) or an already-resolved
     * {@link DirectoryTreeInfo} (sequential mode). Unifies the post-loop result collection.
     */
    private static class DeferredSubdir {
        // Use RecursiveTask instead of TreeWalker to avoid JSpecify @Nullable restriction on non-static inner class types
        @Nullable private final RecursiveTask<DirectoryTreeInfo> forkedTask;
        @Nullable private final DirectoryTreeInfo resolvedResult;
        @Nullable final String symlinkName;

        DeferredSubdir(RecursiveTask<DirectoryTreeInfo> forkedTask, @Nullable String symlinkName) {
            this.forkedTask = forkedTask;
            this.resolvedResult = null;
            this.symlinkName = symlinkName;
        }

        DeferredSubdir(DirectoryTreeInfo resolvedResult, @Nullable String symlinkName) {
            this.forkedTask = null;
            this.resolvedResult = resolvedResult;
            this.symlinkName = symlinkName;
        }

        @SuppressWarnings("DataFlowIssue") // Exactly one of forkedTask/resolvedResult is always non-null
        DirectoryTreeInfo resolve() {
            return forkedTask != null ? forkedTask.join() : resolvedResult;
        }
    }

    private static class DirectoryTreeInfo {
        final String internedAbsPath;
        String internedName;
        AccessType accessType;

        final List<FileSystemLocationSnapshot> cachedChildren;
        final List<FileToHash> filesToHash;
        final List<DirectoryTreeInfo> uncachedSubdirectories;

        // Non-null when this entire subtree was resolved from cache
        @Nullable DirectorySnapshot cachedSnapshot;

        // Filtering state (set by sequential/filtered walks only)
        boolean isFiltered;
        Set<DirectoryTreeInfo> filteredSubdirectories = Collections.emptySet();

        DirectoryTreeInfo(
            String internedAbsPath, String internedName, AccessType accessType,
            List<FileSystemLocationSnapshot> cachedChildren,
            List<FileToHash> filesToHash,
            List<DirectoryTreeInfo> uncachedSubdirectories
        ) {
            this.internedAbsPath = internedAbsPath;
            this.internedName = internedName;
            this.accessType = accessType;
            this.cachedChildren = cachedChildren;
            this.filesToHash = filesToHash;
            this.uncachedSubdirectories = uncachedSubdirectories;
            this.cachedSnapshot = null;
        }

        private DirectoryTreeInfo(String internedAbsPath, String internedName, AccessType accessType, DirectorySnapshot cachedSnapshot) {
            this.internedAbsPath = internedAbsPath;
            this.internedName = internedName;
            this.accessType = accessType;
            this.cachedChildren = Collections.emptyList();
            this.filesToHash = Collections.emptyList();
            this.uncachedSubdirectories = Collections.emptyList();
            this.cachedSnapshot = cachedSnapshot;
        }

        static DirectoryTreeInfo fullyCached(String internedAbsPath, String internedName, AccessType accessType, DirectorySnapshot snapshot) {
            return new DirectoryTreeInfo(internedAbsPath, internedName, accessType, snapshot);
        }

        void applySymlinkOverrides(String symlinkName) {
            internedName = symlinkName;
            accessType = AccessType.VIA_SYMLINK;
            if (cachedSnapshot != null) {
                cachedSnapshot = new DirectorySnapshot(
                    cachedSnapshot.getAbsolutePath(), symlinkName,
                    AccessType.VIA_SYMLINK, cachedSnapshot.getHash(),
                    cachedSnapshot.getChildren());
            }
        }

        void collectAllFiles(List<FileToHash> accumulator) {
            accumulator.addAll(filesToHash);
            for (DirectoryTreeInfo sub : uncachedSubdirectories) {
                sub.collectAllFiles(accumulator);
            }
        }

        boolean hasFilteredDescendants() {
            if (isFiltered) {
                return true;
            }
            for (DirectoryTreeInfo sub : uncachedSubdirectories) {
                if (sub.hasFilteredDescendants()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class FileToHash {
        final Path path;
        final String internedName;
        final BasicFileAttributes attrs;
        final AccessType accessType;
        final SymbolicLinkMapping mapping;
        final ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots;

        // Written by Phase 2, read by Phase 3. Safe: ForkJoinPool.invoke() provides happens-before.
        @Nullable FileSystemLeafSnapshot result;

        FileToHash(Path path, String internedName, BasicFileAttributes attrs, AccessType accessType,
                   SymbolicLinkMapping mapping, ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots) {
            this.path = path;
            this.internedName = internedName;
            this.attrs = attrs;
            this.accessType = accessType;
            this.mapping = mapping;
            this.previouslyKnownSnapshots = previouslyKnownSnapshots;
        }
    }

    // ---- Reused inner types ----

    private interface SymbolicLinkMapping {
        String remapAbsolutePath(Path path);

        @CheckReturnValue
        SymbolicLinkMapping withNewMapping(String source, String target, Iterable<String> relativePathSegments);

        Iterable<String> getRemappedSegments(Iterable<String> segments);
    }

    private static class DefaultSymbolicLinkMapping implements SymbolicLinkMapping {
        private final String sourcePath;
        private final String targetPath;
        private final Iterable<String> prefixRelativePath;

        DefaultSymbolicLinkMapping(String sourcePath, String targetPath, Iterable<String> prefixRelativePath) {
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.prefixRelativePath = prefixRelativePath;
        }

        @Override
        public String remapAbsolutePath(Path dir) {
            return remapAbsolutePath(dir.toString());
        }

        public String remapAbsolutePath(String absolutePath) {
            if (absolutePath.equals(targetPath)) {
                return sourcePath;
            }
            if (absolutePath.startsWith(targetPath) && absolutePath.charAt(targetPath.length()) == File.separatorChar) {
                return sourcePath + File.separatorChar + absolutePath.substring(targetPath.length() + 1);
            }
            throw new IllegalArgumentException("Cannot remap path '" + absolutePath + "' which does not have '" + targetPath + "' as a prefix");
        }

        @Override
        public SymbolicLinkMapping withNewMapping(String source, String target, Iterable<String> relativePathSegments) {
            return new DefaultSymbolicLinkMapping(remapAbsolutePath(source), target, getRemappedSegments(relativePathSegments));
        }

        @Override
        public Iterable<String> getRemappedSegments(Iterable<String> segments) {
            return Iterables.concat(prefixRelativePath, segments);
        }
    }

    @VisibleForTesting
    static class DefaultExcludes {
        private final ImmutableSet<String> excludeFileNames;
        private final ImmutableSet<String> excludedDirNames;
        private final Predicate<String> excludedFileNameSpec;

        public DefaultExcludes(Collection<String> defaultExcludes) {
            final List<String> excludeFiles = new ArrayList<>();
            final List<String> excludeDirs = new ArrayList<>();
            final List<Predicate<String>> excludeFileSpecs = new ArrayList<>();
            for (String defaultExclude : defaultExcludes) {
                if (defaultExclude.startsWith("**/")) {
                    defaultExclude = defaultExclude.substring(3);
                }
                int length = defaultExclude.length();
                if (defaultExclude.endsWith("/**")) {
                    excludeDirs.add(defaultExclude.substring(0, length - 3));
                } else {
                    int firstStar = defaultExclude.indexOf('*');
                    if (firstStar == -1) {
                        excludeFiles.add(defaultExclude);
                    } else {
                        String startStr = defaultExclude.substring(0, firstStar);
                        String endStr = defaultExclude.substring(firstStar + 1, length);
                        Predicate<String> start = firstStar == 0 ? it -> true : it -> it.startsWith(startStr);
                        Predicate<String> end = firstStar == length - 1 ? it -> true : it -> it.endsWith(endStr);
                        excludeFileSpecs.add(start.and(end));
                    }
                }
            }

            this.excludeFileNames = ImmutableSet.copyOf(excludeFiles);
            this.excludedFileNameSpec = excludeFileSpecs.stream().reduce(it -> false, Predicate::or);
            this.excludedDirNames = ImmutableSet.copyOf(excludeDirs);
        }

        public boolean excludeDir(String name) {
            return excludedDirNames.contains(name);
        }

        public boolean excludeFile(String name) {
            return excludeFileNames.contains(name) || excludedFileNameSpec.test(name);
        }

    }
}
