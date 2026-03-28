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
import java.util.stream.Stream;

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

        // Phase 1: tree walk -> skeleton
        DirectoryTreeInfo skeleton;
        if (predicate == null) {
            skeleton = forkJoinPool.invoke(
                new TreeWalkTask(rootPath, EMPTY_SYMBOLIC_LINK_MAPPING, PersistentSet.of(), snapshots));
        } else {
            skeleton = new SequentialTreeWalker(predicate, snapshots, EMPTY_SYMBOLIC_LINK_MAPPING)
                .walk(rootPath, new ArrayList<>(), new HashSet<>());
        }

        // Phase 2: parallel file hashing
        List<FileToHash> allFiles = new ArrayList<>();
        skeleton.collectAllFiles(allFiles);
        if (!allFiles.isEmpty()) {
            hashFiles(allFiles);
        }

        // Phase 3: build snapshot tree (in-memory)
        DirectorySnapshot result = buildSnapshotFromSkeleton(skeleton, unfilteredSnapshotRecorder);
        if (!skeleton.hasFilteredDescendants()) {
            unfilteredSnapshotRecorder.accept(result);
        }
        return result;
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
                    SymbolicLinkMapping newMapping = EMPTY_SYMBOLIC_LINK_MAPPING.withNewMapping(
                        file.toString(), targetDir.toString(), Collections.emptyList());

                    collector.recordVisitHierarchy();
                    DirectoryTreeInfo skeleton;
                    if (predicate == null) {
                        skeleton = forkJoinPool.invoke(
                            new TreeWalkTask(targetDir, newMapping, PersistentSet.of(), snapshots));
                    } else {
                        skeleton = new SequentialTreeWalker(predicate, snapshots, newMapping)
                            .walk(targetDir, new ArrayList<>(), new HashSet<>());
                    }

                    List<FileToHash> allFiles = new ArrayList<>();
                    skeleton.collectAllFiles(allFiles);
                    if (!allFiles.isEmpty()) {
                        hashFiles(allFiles);
                    }

                    DirectorySnapshot targetSnapshot = buildSnapshotFromSkeleton(skeleton, unfilteredSnapshotRecorder);
                    DirectorySnapshot snapshotViaSymlink = new DirectorySnapshot(
                        targetSnapshot.getAbsolutePath(), internedName,
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

    @SuppressWarnings("StreamResourceLeak")
    private Stream<Path> listDirectoryChildren(Path dir) {
        try {
            return Files.list(dir);
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

    // ---- Phase 1 (Parallel): TreeWalkTask ----

    private class TreeWalkTask extends RecursiveTask<DirectoryTreeInfo> {
        private final Path dir;
        private final SymbolicLinkMapping mapping;
        private final PersistentSet<String> parentDirPaths;
        private final ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots;

        TreeWalkTask(Path dir, SymbolicLinkMapping mapping, PersistentSet<String> parentDirPaths,
                     ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots) {
            this.dir = dir;
            this.mapping = mapping;
            this.parentDirPaths = parentDirPaths;
            this.previouslyKnownSnapshots = previouslyKnownSnapshots;
        }

        @Override
        protected DirectoryTreeInfo compute() {
            String internedName = getInternedFileName(dir);
            String internedAbsPath = intern(mapping.remapAbsolutePath(dir));

            FileSystemLocationSnapshot cached = previouslyKnownSnapshots.get(internedAbsPath);
            if (cached instanceof DirectorySnapshot) {
                return DirectoryTreeInfo.fullyCached(internedAbsPath, internedName, AccessType.DIRECT, (DirectorySnapshot) cached);
            } else if (cached != null) {
                throw new IllegalStateException("Expected a previously known directory snapshot at " + internedAbsPath + " but got " + cached);
            }

            PersistentSet<String> currentPaths = parentDirPaths.plus(dir.toString());

            List<FileSystemLocationSnapshot> cachedChildren = new ArrayList<>();
            List<FileToHash> filesToHash = new ArrayList<>();
            List<TreeWalkTask> forkedSubdirTasks = new ArrayList<>();
            List<SymlinkDirEntry> forkedSymlinkDirTasks = new ArrayList<>();

            try (Stream<Path> children = listDirectoryChildren(dir)) {
                collector.recordVisitDirectory();
                children.forEach(child -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        String childName = getInternedFileName(child);

                        if (attrs.isDirectory()) {
                            if (!defaultExcludes.excludeDir(childName)) {
                                TreeWalkTask task = new TreeWalkTask(child, mapping, currentPaths, previouslyKnownSnapshots);
                                task.fork();
                                forkedSubdirTasks.add(task);
                            }
                        } else if (attrs.isSymbolicLink()) {
                            collector.recordVisitFile();
                            BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(child, attrs);
                            if (targetAttrs.isDirectory()) {
                                handleSymlinkDir(child, childName, currentPaths, forkedSymlinkDirTasks);
                            } else if (!defaultExcludes.excludeFile(childName)) {
                                addFileOrCached(child, childName, targetAttrs, AccessType.VIA_SYMLINK, cachedChildren, filesToHash);
                            }
                        } else {
                            collector.recordVisitFile();
                            if (!defaultExcludes.excludeFile(childName)) {
                                addFileOrCached(child, childName, attrs, AccessType.DIRECT, cachedChildren, filesToHash);
                            }
                        }
                    } catch (IOException e) {
                        collector.recordVisitFileFailed();
                        if (isNotFileSystemLoopException(e)) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }

            List<DirectoryTreeInfo> uncachedSubdirs = new ArrayList<>();
            for (TreeWalkTask task : forkedSubdirTasks) {
                DirectoryTreeInfo subdirInfo = task.join();
                if (subdirInfo.cachedSnapshot != null) {
                    cachedChildren.add(subdirInfo.cachedSnapshot);
                } else {
                    uncachedSubdirs.add(subdirInfo);
                }
            }
            for (SymlinkDirEntry entry : forkedSymlinkDirTasks) {
                DirectoryTreeInfo subdirInfo = entry.task.join();
                subdirInfo.internedName = entry.symlinkName;
                subdirInfo.accessType = AccessType.VIA_SYMLINK;
                if (subdirInfo.cachedSnapshot != null) {
                    DirectorySnapshot wrapped = new DirectorySnapshot(
                        subdirInfo.cachedSnapshot.getAbsolutePath(), entry.symlinkName,
                        AccessType.VIA_SYMLINK, subdirInfo.cachedSnapshot.getHash(),
                        subdirInfo.cachedSnapshot.getChildren());
                    cachedChildren.add(wrapped);
                } else {
                    uncachedSubdirs.add(subdirInfo);
                }
            }

            return new DirectoryTreeInfo(internedAbsPath, internedName, AccessType.DIRECT,
                cachedChildren, filesToHash, uncachedSubdirs);
        }

        private void handleSymlinkDir(Path child, String symlinkName, PersistentSet<String> currentPaths,
                                       List<SymlinkDirEntry> forkedSymlinkDirTasks) {
            try {
                Path targetDir = child.toRealPath();
                String targetDirString = targetDir.toString();
                if (!currentPaths.contains(targetDirString)) {
                    SymbolicLinkMapping newMapping = mapping.withNewMapping(
                        child.toString(), targetDirString, Collections.emptyList());
                    collector.recordVisitHierarchy();
                    TreeWalkTask task = new TreeWalkTask(targetDir, newMapping, currentPaths, previouslyKnownSnapshots);
                    task.fork();
                    forkedSymlinkDirTasks.add(new SymlinkDirEntry(symlinkName, task));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", child), e);
            }
        }

        private void addFileOrCached(Path child, String childName, BasicFileAttributes attrs, AccessType accessType,
                                      List<FileSystemLocationSnapshot> cachedChildren, List<FileToHash> filesToHash) {
            String fileAbsPath = intern(mapping.remapAbsolutePath(child));
            FileSystemLocationSnapshot cachedFile = previouslyKnownSnapshots.get(fileAbsPath);
            if (cachedFile instanceof FileSystemLeafSnapshot) {
                cachedChildren.add(cachedFile);
            } else {
                filesToHash.add(new FileToHash(child, childName, attrs, accessType, mapping, previouslyKnownSnapshots));
            }
        }
    }

    // ---- Phase 1 (Sequential): SequentialTreeWalker ----

    private class SequentialTreeWalker {
        private final SnapshottingFilter.DirectoryWalkerPredicate predicate;
        private final ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots;
        private final SymbolicLinkMapping rootMapping;

        SequentialTreeWalker(
            SnapshottingFilter.DirectoryWalkerPredicate predicate,
            ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots,
            SymbolicLinkMapping rootMapping
        ) {
            this.predicate = predicate;
            this.previouslyKnownSnapshots = previouslyKnownSnapshots;
            this.rootMapping = rootMapping;
        }

        DirectoryTreeInfo walk(Path dir, List<String> relativePathSegments, Set<String> parentDirPaths) {
            return walkWithMapping(dir, relativePathSegments, parentDirPaths, rootMapping);
        }

        private DirectoryTreeInfo walkWithMapping(Path dir, List<String> relativePathSegments, Set<String> parentDirPaths, SymbolicLinkMapping mapping) {
            String internedName = getInternedFileName(dir);
            String internedAbsPath = intern(mapping.remapAbsolutePath(dir));

            // No directory-level cache reuse when predicate is present (existing behavior)

            parentDirPaths.add(dir.toString());

            List<FileSystemLocationSnapshot> cachedChildren = new ArrayList<>();
            List<FileToHash> filesToHash = new ArrayList<>();
            List<DirectoryTreeInfo> uncachedSubdirs = new ArrayList<>();
            boolean isFiltered = false;
            Set<DirectoryTreeInfo> filteredSubdirs = new HashSet<>();

            try (Stream<Path> children = listDirectoryChildren(dir)) {
                collector.recordVisitDirectory();
                for (Path child : (Iterable<Path>) children::iterator) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        String childName = getInternedFileName(child);

                        if (attrs.isDirectory()) {
                            Iterable<String> segments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                            if (shouldVisit(child, childName, true, segments, mapping)) {
                                relativePathSegments.add(childName);
                                DirectoryTreeInfo subdirInfo = walkWithMapping(child, relativePathSegments, parentDirPaths, mapping);
                                uncachedSubdirs.add(subdirInfo);
                                if (subdirInfo.isFiltered || subdirInfo.hasFilteredDescendants()) {
                                    filteredSubdirs.add(subdirInfo);
                                    isFiltered = true;
                                }
                                relativePathSegments.remove(relativePathSegments.size() - 1);
                            } else {
                                isFiltered = true;
                            }
                        } else if (attrs.isSymbolicLink()) {
                            collector.recordVisitFile();
                            BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(child, attrs);
                            if (targetAttrs.isDirectory()) {
                                Iterable<String> segments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                                if (shouldVisit(child, childName, true, segments, mapping)) {
                                    DirectoryTreeInfo symlinkInfo = handleSequentialSymlinkDir(
                                        child, childName, relativePathSegments, parentDirPaths, mapping);
                                    if (symlinkInfo != null) {
                                        symlinkInfo.internedName = childName;
                                        symlinkInfo.accessType = AccessType.VIA_SYMLINK;
                                        uncachedSubdirs.add(symlinkInfo);
                                        if (symlinkInfo.isFiltered || symlinkInfo.hasFilteredDescendants()) {
                                            filteredSubdirs.add(symlinkInfo);
                                            isFiltered = true;
                                        }
                                    }
                                } else {
                                    isFiltered = true;
                                }
                            } else {
                                Iterable<String> segments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                                if (shouldVisit(child, childName, false, segments, mapping)) {
                                    addSequentialFileOrCached(child, childName, targetAttrs, AccessType.VIA_SYMLINK, mapping, cachedChildren, filesToHash);
                                } else {
                                    isFiltered = true;
                                }
                            }
                        } else {
                            collector.recordVisitFile();
                            Iterable<String> segments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                            if (shouldVisit(child, childName, false, segments, mapping)) {
                                addSequentialFileOrCached(child, childName, attrs, AccessType.DIRECT, mapping, cachedChildren, filesToHash);
                            } else {
                                isFiltered = true;
                            }
                        }
                    } catch (IOException e) {
                        collector.recordVisitFileFailed();
                        if (isNotFileSystemLoopException(e)) {
                            String failedName = getInternedFileName(child);
                            boolean isDirectory = Files.isDirectory(child);
                            Iterable<String> segments = Iterables.concat(relativePathSegments, Collections.singleton(failedName));
                            if (shouldVisit(child, failedName, isDirectory, segments, mapping)) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    }
                }
            }

            parentDirPaths.remove(dir.toString());

            DirectoryTreeInfo info = new DirectoryTreeInfo(internedAbsPath, internedName, AccessType.DIRECT,
                cachedChildren, filesToHash, uncachedSubdirs);
            info.isFiltered = isFiltered;
            info.filteredSubdirectories = filteredSubdirs;
            return info;
        }

        @Nullable
        private DirectoryTreeInfo handleSequentialSymlinkDir(
            Path symlinkPath, String childName, List<String> parentRelativeSegments,
            Set<String> parentDirPaths, SymbolicLinkMapping mapping
        ) {
            try {
                Path targetDir = symlinkPath.toRealPath();
                String targetDirString = targetDir.toString();
                if (!parentDirPaths.contains(targetDirString)) {
                    Iterable<String> symlinkSegments = Iterables.concat(parentRelativeSegments, Collections.singleton(childName));
                    SymbolicLinkMapping newMapping = mapping.withNewMapping(
                        symlinkPath.toString(), targetDirString, symlinkSegments);
                    collector.recordVisitHierarchy();
                    return walkWithMapping(targetDir, new ArrayList<>(), parentDirPaths, newMapping);
                }
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", symlinkPath), e);
            }
        }

        private void addSequentialFileOrCached(Path child, String childName, BasicFileAttributes attrs, AccessType accessType,
                                                SymbolicLinkMapping mapping,
                                                List<FileSystemLocationSnapshot> cachedChildren, List<FileToHash> filesToHash) {
            String fileAbsPath = intern(mapping.remapAbsolutePath(child));
            FileSystemLocationSnapshot cachedFile = previouslyKnownSnapshots.get(fileAbsPath);
            if (cachedFile instanceof FileSystemLeafSnapshot) {
                cachedChildren.add(cachedFile);
            } else {
                filesToHash.add(new FileToHash(child, childName, attrs, accessType, mapping, previouslyKnownSnapshots));
            }
        }

        private boolean shouldVisit(Path path, String internedName, boolean isDirectory, Iterable<String> segments, SymbolicLinkMapping mapping) {
            if (isDirectory) {
                if (defaultExcludes.excludeDir(internedName)) {
                    return false;
                }
            } else if (defaultExcludes.excludeFile(internedName)) {
                return false;
            }
            return predicate.test(path, internedName, isDirectory, mapping.getRemappedSegments(segments));
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

    private static class DirectoryTreeInfo {
        final String internedAbsPath;
        String internedName;
        AccessType accessType;

        final List<FileSystemLocationSnapshot> cachedChildren;
        final List<FileToHash> filesToHash;
        final List<DirectoryTreeInfo> uncachedSubdirectories;

        // Non-null when this entire subtree was resolved from cache
        final @Nullable DirectorySnapshot cachedSnapshot;

        // Filtering state (set by SequentialTreeWalker only)
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

    private static class SymlinkDirEntry {
        final String symlinkName;
        final TreeWalkTask task;

        SymlinkDirEntry(String symlinkName, TreeWalkTask task) {
            this.symlinkName = symlinkName;
            this.task = task;
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
                        Predicate<String> start = firstStar == 0
                            ? it -> true
                            : new StartMatcher(defaultExclude.substring(0, firstStar));
                        Predicate<String> end = firstStar == length - 1
                            ? it -> true
                            : new EndMatcher(defaultExclude.substring(firstStar + 1, length));
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

        private static class EndMatcher implements Predicate<String> {
            private final String end;

            EndMatcher(String end) {
                this.end = end;
            }

            @Override
            public boolean test(String element) {
                return element.endsWith(end);
            }
        }

        private static class StartMatcher implements Predicate<String> {
            private final String start;

            StartMatcher(String start) {
                this.start = start;
            }

            @Override
            public boolean test(String element) {
                return element.startsWith(start);
            }
        }
    }
}
