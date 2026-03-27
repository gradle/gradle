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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS;

/**
 * For creating {@link DirectorySnapshot}s of directories.
 *
 * When an {@link ExecutorService} is provided, file hashing within each directory
 * is parallelized for directories exceeding {@link #PARALLEL_FILE_THRESHOLD} files.
 * Directory tree traversal remains sequential (depth-first).
 */
public class DirectorySnapshotter {
    @VisibleForTesting
    static final int PARALLEL_FILE_THRESHOLD = 20;

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
    @Nullable
    private final ExecutorService executor;

    public DirectorySnapshotter(FileHasher hasher, Interner<String> stringInterner, Collection<String> defaultExcludes, DirectorySnapshotterStatistics.Collector collector) {
        this(hasher, stringInterner, defaultExcludes, collector, null);
    }

    public DirectorySnapshotter(FileHasher hasher, Interner<String> stringInterner, Collection<String> defaultExcludes, DirectorySnapshotterStatistics.Collector collector, @Nullable ExecutorService executor) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.defaultExcludes = new DefaultExcludes(defaultExcludes);
        this.collector = collector;
        this.executor = executor;
    }

    /**
     * Snapshots a directory, reusing existing previously known snapshots.
     *
     * Follows symlinks and includes them in the returned snapshot.
     * Snapshots of followed symlinks are marked with {@link AccessType#VIA_SYMLINK}.
     *
     * @param absolutePath The absolute path of the directory to snapshot.
     * @param predicate A predicate that determines which files to include in the snapshot.
     *                  {@code null} means to include everything.
     * @param previouslyKnownSnapshots Snapshots already known to exist in the file system.
     * @param unfilteredSnapshotRecorder If the returned snapshot is filtered by the predicate, i.e. it doesn't have all the contents of the directory,
     * then this consumer will receive all the unfiltered snapshots within the snapshot directory.
     * For example, if an element of a directory is filtered out, the consumer will receive all the non-filtered out
     * file snapshots and all the non-filtered directory snapshots in the directory.
     * @return The (possible filtered) snapshot of the directory.
     */
    public FileSystemLocationSnapshot snapshot(
        String absolutePath,
        SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate,
        Map<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots,
        Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder
    ) {
        AtomicBoolean hasBeenFiltered = new AtomicBoolean();
        Path rootPath = Paths.get(absolutePath);
        DirectoryVisitor visitor = new DirectoryVisitor(
            predicate, hasBeenFiltered, hasher, stringInterner, defaultExcludes,
            collector, EMPTY_SYMBOLIC_LINK_MAPPING, previouslyKnownSnapshots,
            unfilteredSnapshotRecorder, executor);

        collector.recordVisitHierarchy();

        BasicFileAttributes rootAttrs;
        try {
            rootAttrs = Files.readAttributes(rootPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            // Root path doesn't exist or can't be read
            collector.recordVisitFileFailed();
            throw new UncheckedIOException(e);
        }

        String rootName = visitor.getInternedFileName(rootPath);

        if (rootAttrs.isDirectory()) {
            // relativePathSegments is empty for root - RelativePathTracker.getSegments()
            // does not include the root directory name, only child segments.
            // Both collections are mutable - sequential traversal uses push/pop.
            visitor.processDirectory(rootPath, new ArrayList<>(), new HashSet<>());
        } else {
            // Root is a regular file or symlink (edge case - normally snapshot() is called on directories)
            collector.recordVisitFile();
            visitor.processFileAsRoot(rootPath, rootName, rootAttrs);
        }

        FileSystemLocationSnapshot result = visitor.getResult();
        if (!hasBeenFiltered.get()) {
            unfilteredSnapshotRecorder.accept(result);
        }
        return result;
    }

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

        public DefaultSymbolicLinkMapping(String sourcePath, String targetPath, Iterable<String> prefixRelativePath) {
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

            public EndMatcher(String end) {
                this.end = end;
            }

            @Override
            public boolean test(String element) {
                return element.endsWith(end);
            }
        }

        private static class StartMatcher implements Predicate<String> {
            private final String start;

            public StartMatcher(String start) {
                this.start = start;
            }

            @Override
            public boolean test(String element) {
                return element.startsWith(start);
            }
        }
    }

    /**
     * Holds information about a file that needs to be snapshotted (hashed).
     * Used to batch file hashing for parallel execution.
     */
    private static class FileToSnapshot {
        final Path path;
        final String internedName;
        final BasicFileAttributes attrs;
        final AccessType accessType;

        FileToSnapshot(Path path, String internedName, BasicFileAttributes attrs, AccessType accessType) {
            this.path = path;
            this.internedName = internedName;
            this.attrs = attrs;
            this.accessType = accessType;
        }
    }

    /**
     * Visits a directory tree using manual recursive traversal via {@link Files#list(Path)}.
     * File hashing within each directory can be parallelized when an executor is provided.
     * Directory tree traversal is always sequential (depth-first).
     */
    private static class DirectoryVisitor {
        private final FilteredTrackingMerkleDirectorySnapshotBuilder builder;
        private final SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate;
        private final AtomicBoolean hasBeenFiltered;
        private final FileHasher hasher;
        private final Interner<String> stringInterner;
        private final DefaultExcludes defaultExcludes;
        private final DirectorySnapshotterStatistics.Collector collector;
        private final SymbolicLinkMapping symbolicLinkMapping;
        private final Set<FileSystemLocationSnapshot> filteredDirectorySnapshots = new HashSet<>();
        private final ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots;
        private final Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder;
        @Nullable
        private final ExecutorService executor;

        public DirectoryVisitor(
            SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate,
            AtomicBoolean hasBeenFiltered,
            FileHasher hasher,
            Interner<String> stringInterner,
            DefaultExcludes defaultExcludes,
            DirectorySnapshotterStatistics.Collector collector,
            SymbolicLinkMapping symbolicLinkMapping,
            Map<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots,
            Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder,
            @Nullable ExecutorService executor
        ) {
            this.builder = FilteredTrackingMerkleDirectorySnapshotBuilder.sortingRequired(this::recordUnfilteredSnapshot);
            this.predicate = predicate;
            this.hasBeenFiltered = hasBeenFiltered;
            this.hasher = hasher;
            this.stringInterner = stringInterner;
            this.defaultExcludes = defaultExcludes;
            this.collector = collector;
            this.symbolicLinkMapping = symbolicLinkMapping;
            this.previouslyKnownSnapshots = ImmutableMap.copyOf(previouslyKnownSnapshots);
            this.unfilteredSnapshotRecorder = unfilteredSnapshotRecorder;
            this.executor = executor;
        }

        private void recordUnfilteredSnapshot(FileSystemLocationSnapshot snapshot) {
            if (snapshot.getType() != FileType.Directory || !filteredDirectorySnapshots.contains(snapshot)) {
                unfilteredSnapshotRecorder.accept(snapshot);
            }
        }

        /**
         * Processes a directory: lists children, hashes files (possibly in parallel),
         * and recurses into subdirectories sequentially.
         *
         * @param dir the directory path to process
         * @param relativePathSegments path segments from root to this directory (inclusive)
         * @param parentDirPaths absolute paths of all ancestor directories (for cycle detection)
         */
        void processDirectory(Path dir, List<String> relativePathSegments, Set<String> parentDirPaths) {
            String internedName = getInternedFileName(dir);
            String internedAbsPath = intern(symbolicLinkMapping.remapAbsolutePath(dir));

            // TODO Reuse previous directory snapshot even when filtering is enabled
            if (predicate == null) {
                FileSystemLocationSnapshot previouslyKnownSnapshot = previouslyKnownSnapshots.get(internedAbsPath);
                if (previouslyKnownSnapshot instanceof DirectorySnapshot) {
                    builder.visitDirectory((DirectorySnapshot) previouslyKnownSnapshot);
                    return;
                } else if (previouslyKnownSnapshot != null) {
                    throw new IllegalStateException("Expected a previously known directory snapshot at " + internedAbsPath + " but got " + previouslyKnownSnapshot);
                }
            }

            // Mutable set for cycle detection - sequential traversal allows add/remove
            parentDirPaths.add(dir.toString());

            List<FileToSnapshot> filesToHash = new ArrayList<>();
            List<DirToVisit> dirsToRecurse = new ArrayList<>();

            try (Stream<Path> childStream = listDirectoryChildren(dir)) {
                collector.recordVisitDirectory();
                builder.enterDirectory(AccessType.DIRECT, internedAbsPath, internedName, INCLUDE_EMPTY_DIRS);

                childStream.forEach(child -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        String childName = getInternedFileName(child);

                        if (attrs.isDirectory()) {
                            Iterable<String> childSegments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                            if (shouldVisit(child, childName, true, childSegments)) {
                                // Push/snapshot/pop to avoid copying the list per child dir
                                relativePathSegments.add(childName);
                                dirsToRecurse.add(new DirToVisit(child, new ArrayList<>(relativePathSegments)));
                                relativePathSegments.remove(relativePathSegments.size() - 1);
                            }
                        } else if (attrs.isSymbolicLink()) {
                            collector.recordVisitFile();
                            BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(child, attrs);
                            if (targetAttrs.isDirectory()) {
                                handleSymlinkToDirectory(child, childName, relativePathSegments, parentDirPaths);
                            } else {
                                Iterable<String> childSegments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                                if (shouldVisit(child, childName, false, childSegments)) {
                                    filesToHash.add(new FileToSnapshot(child, childName, targetAttrs, AccessType.VIA_SYMLINK));
                                }
                            }
                        } else {
                            collector.recordVisitFile();
                            Iterable<String> childSegments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                            if (shouldVisit(child, childName, false, childSegments)) {
                                filesToHash.add(new FileToSnapshot(child, childName, attrs, AccessType.DIRECT));
                            }
                        }
                    } catch (IOException e) {
                        handleFailed(child, e, relativePathSegments);
                    }
                });
            }

            // Submit file hashing first (non-blocking), then recurse into subdirectories.
            // This overlaps file I/O with subdirectory traversal: while the main thread
            // descends into subdirectories, worker threads hash this directory's files.
            // By the time we join, most futures are already complete.
            List<Future<FileSystemLeafSnapshot>> fileFutures = submitFileHashing(filesToHash);

            // Recurse into subdirectories while file hashing runs in background
            for (DirToVisit dirToVisit : dirsToRecurse) {
                processDirectory(dirToVisit.path, dirToVisit.relativePathSegments, parentDirPaths);
            }

            // Join file results and add to builder - likely already complete
            joinAndAddFiles(fileFutures, filesToHash);

            parentDirPaths.remove(dir.toString());

            boolean currentLevelComplete = builder.isCurrentLevelUnfiltered();
            FileSystemLocationSnapshot currentLevel = builder.leaveDirectory();
            if (!currentLevelComplete) {
                filteredDirectorySnapshots.add(currentLevel);
            }
        }

        /**
         * Processes a non-directory root (regular file or symlink to file).
         * This is an edge case - normally snapshot() is called only on directories.
         */
        void processFileAsRoot(Path file, String internedName, BasicFileAttributes attrs) {
            if (attrs.isSymbolicLink()) {
                BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(file, attrs);
                if (targetAttrs.isDirectory()) {
                    // Symlink to directory at root level - follow it.
                    // The root symlink name does not contribute to relative path segments
                    // (same as RelativePathTracker which stores root name separately).
                    AtomicBoolean symlinkHasBeenFiltered = new AtomicBoolean();
                    DirectorySnapshot targetSnapshot = followSymlinkAsRoot(file, symlinkHasBeenFiltered);
                    if (targetSnapshot != null) {
                        DirectorySnapshot snapshotViaSymlink = new DirectorySnapshot(
                            targetSnapshot.getAbsolutePath(), internedName,
                            AccessType.VIA_SYMLINK, targetSnapshot.getHash(), targetSnapshot.getChildren()
                        );
                        builder.visitDirectory(snapshotViaSymlink);
                        if (symlinkHasBeenFiltered.get()) {
                            hasBeenFiltered.set(true);
                        }
                    }
                } else {
                    builder.visitLeafElement(snapshotFile(file, internedName, targetAttrs, AccessType.VIA_SYMLINK));
                }
            } else {
                builder.visitLeafElement(snapshotFile(file, internedName, attrs, AccessType.DIRECT));
            }
        }

        private void handleSymlinkToDirectory(Path symlinkPath, String internedFileName, List<String> parentRelativeSegments, Set<String> parentDirPaths) {
            Iterable<String> symlinkSegments = Iterables.concat(parentRelativeSegments, Collections.singleton(internedFileName));
            if (!shouldVisit(symlinkPath, internedFileName, true, symlinkSegments)) {
                return;
            }

            AtomicBoolean symlinkHasBeenFiltered = new AtomicBoolean();
            DirectorySnapshot targetSnapshot = followSymlink(symlinkPath, internedFileName, symlinkHasBeenFiltered, parentRelativeSegments, parentDirPaths);
            if (targetSnapshot != null) {
                DirectorySnapshot directorySnapshotAccessedViaSymlink = new DirectorySnapshot(
                    targetSnapshot.getAbsolutePath(),
                    internedFileName,
                    AccessType.VIA_SYMLINK,
                    targetSnapshot.getHash(),
                    targetSnapshot.getChildren()
                );
                builder.visitDirectory(directorySnapshotAccessedViaSymlink);
                boolean symlinkFiltered = symlinkHasBeenFiltered.get();
                if (symlinkFiltered) {
                    filteredDirectorySnapshots.add(directorySnapshotAccessedViaSymlink);
                    builder.markCurrentLevelAsFiltered();
                    hasBeenFiltered.set(true);
                }
            }
        }

        /**
         * Follows a symlink that is the root of the snapshot operation.
         * The root name does not contribute to relative path segments.
         */
        @Nullable
        private DirectorySnapshot followSymlinkAsRoot(Path file, AtomicBoolean symlinkHasBeenFiltered) {
            try {
                Path targetDir = file.toRealPath();
                String targetDirString = targetDir.toString();
                // Root-level symlink: mapping prefix is empty (root name not part of relative segments)
                SymbolicLinkMapping newMapping = symbolicLinkMapping.withNewMapping(file.toString(), targetDirString, Collections.emptyList());

                DirectoryVisitor subtreeVisitor = new DirectoryVisitor(
                    predicate, symlinkHasBeenFiltered, hasher, stringInterner, defaultExcludes,
                    collector, newMapping, previouslyKnownSnapshots, unfilteredSnapshotRecorder, executor);

                collector.recordVisitHierarchy();
                subtreeVisitor.processDirectory(targetDir, new ArrayList<>(), new HashSet<>());

                return (DirectorySnapshot) subtreeVisitor.getResult();
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
            }
        }

        @Nullable
        private DirectorySnapshot followSymlink(Path file, String internedFileName, AtomicBoolean symlinkHasBeenFiltered, List<String> parentRelativeSegments, Set<String> parentDirPaths) {
            try {
                Path targetDir = file.toRealPath();
                String targetDirString = targetDir.toString();
                if (!parentDirPaths.contains(targetDirString)) {
                    Iterable<String> symlinkSegments = Iterables.concat(parentRelativeSegments, Collections.singleton(internedFileName));
                    SymbolicLinkMapping newMapping = symbolicLinkMapping.withNewMapping(file.toString(), targetDirString, symlinkSegments);

                    DirectoryVisitor subtreeVisitor = new DirectoryVisitor(
                        predicate,
                        symlinkHasBeenFiltered,
                        hasher,
                        stringInterner,
                        defaultExcludes,
                        collector,
                        newMapping,
                        previouslyKnownSnapshots,
                        unfilteredSnapshotRecorder,
                        executor);

                    collector.recordVisitHierarchy();
                    subtreeVisitor.processDirectory(targetDir, new ArrayList<>(), parentDirPaths);

                    return (DirectorySnapshot) subtreeVisitor.getResult();
                } else {
                    return null;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
            }
        }

        /**
         * Submits file hashing tasks to the executor (non-blocking).
         * Returns futures if parallel, or null if files will be hashed during join.
         */
        @Nullable
        private List<Future<FileSystemLeafSnapshot>> submitFileHashing(List<FileToSnapshot> files) {
            if (executor == null || files.size() < PARALLEL_FILE_THRESHOLD) {
                return null;
            }
            List<Future<FileSystemLeafSnapshot>> futures = new ArrayList<>(files.size());
            for (FileToSnapshot f : files) {
                futures.add(executor.submit(() -> snapshotFile(f.path, f.internedName, f.attrs, f.accessType)));
            }
            return futures;
        }

        /**
         * Joins file hashing results and adds them to the builder.
         * If futures is null (below threshold or no executor), hashes sequentially here.
         */
        private void joinAndAddFiles(
            @Nullable List<Future<FileSystemLeafSnapshot>> futures,
            List<FileToSnapshot> files
        ) {
            if (files.isEmpty()) {
                return;
            }
            if (futures == null) {
                // Sequential path - hash and add inline
                for (FileToSnapshot f : files) {
                    builder.visitLeafElement(snapshotFile(f.path, f.internedName, f.attrs, f.accessType));
                }
                return;
            }
            // Parallel path - collect results from futures
            for (Future<FileSystemLeafSnapshot> future : futures) {
                try {
                    builder.visitLeafElement(future.get());
                } catch (ExecutionException e) {
                    throw UncheckedException.throwAsUncheckedException(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }

        private FileSystemLeafSnapshot snapshotFile(Path absoluteFilePath, String internedName, BasicFileAttributes attrs, AccessType accessType) {
            String internedRemappedAbsoluteFilePath = intern(symbolicLinkMapping.remapAbsolutePath(absoluteFilePath));
            FileSystemLocationSnapshot previouslyKnownSnapshot = previouslyKnownSnapshots.get(internedRemappedAbsoluteFilePath);
            if (previouslyKnownSnapshot != null) {
                if (!(previouslyKnownSnapshot instanceof FileSystemLeafSnapshot)) {
                    throw new IllegalStateException("Expected a previously known leaf snapshot at " + internedRemappedAbsoluteFilePath + ", but found " + previouslyKnownSnapshot);
                }
                return (FileSystemLeafSnapshot) previouslyKnownSnapshot;
            }
            if (attrs.isSymbolicLink()) {
                return new MissingFileSnapshot(internedRemappedAbsoluteFilePath, internedName, accessType);
            } else if (!attrs.isRegularFile()) {
                throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Cannot snapshot %s: not a regular file", internedRemappedAbsoluteFilePath)));
            }
            long lastModified = attrs.lastModifiedTime().toMillis();
            long fileLength = attrs.size();
            FileMetadata metadata = DefaultFileMetadata.file(lastModified, fileLength, accessType);
            HashCode hash = hasher.hash(absoluteFilePath.toFile(), fileLength, lastModified);
            return new RegularFileSnapshot(internedRemappedAbsoluteFilePath, internedName, hash, metadata);
        }

        /**
         * Handles a failed file visit (e.g., permission denied).
         */
        private void handleFailed(Path file, IOException exc, List<String> parentRelativeSegments) {
            collector.recordVisitFileFailed();
            // File loop exceptions are ignored. When we encounter a loop (via symbolic links), we continue,
            // so we include all the other files apart from the loop.
            // This way, we include each file only once.
            if (isNotFileSystemLoopException(exc)) {
                String internedFileName = getInternedFileName(file);
                boolean isDirectory = Files.isDirectory(file);
                Iterable<String> segments = Iterables.concat(parentRelativeSegments, Collections.singleton(internedFileName));
                if (shouldVisit(file, internedFileName, isDirectory, segments)) {
                    throw UncheckedException.throwAsUncheckedException(exc);
                }
            }
        }

        /**
         * Opens a directory stream, recording a failed visit if the directory cannot be opened.
         * On success, the caller is responsible for closing the stream (via try-with-resources).
         */
        @SuppressWarnings("StreamResourceLeak") // Caller closes via try-with-resources
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
                // We emulate the behavior of `Files.walkFileTree(Path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), PathVisitor)`,
                // and return the attributes of the symlink if we can't read the attributes of the target of the symlink.
                return symlinkAttributes;
            }
        }

        private static boolean isNotFileSystemLoopException(@Nullable IOException e) {
            return e != null && !(e instanceof FileSystemLoopException);
        }

        private String intern(String string) {
            return stringInterner.intern(string);
        }

        /**
         * Returns whether we want to visit the given path during our walk, or ignore it completely,
         * based on the directory/file excludes or the provided filtering predicate.
         * Excludes won't mark this walk as `filtered`, only if the `predicate` rejects any entry.
         **/
        private boolean shouldVisit(Path path, String internedName, boolean isDirectory, Iterable<String> segments) {
            if (isDirectory) {
                if (defaultExcludes.excludeDir(internedName)) {
                    return false;
                }
            } else if (defaultExcludes.excludeFile(internedName)) {
                return false;
            }

            if (predicate == null) {
                return true;
            }
            boolean allowed = predicate.test(path, internedName, isDirectory, symbolicLinkMapping.getRemappedSegments(segments));
            if (!allowed) {
                builder.markCurrentLevelAsFiltered();
                hasBeenFiltered.set(true);
            }
            return allowed;
        }

        String getInternedFileName(Path path) {
            // Path also has getFileName() but it creates additional allocations,
            // and since this is on a hot path we optimized it
            String absolutePath = path.toString();
            int lastSep = absolutePath.lastIndexOf(File.separatorChar);
            return intern(lastSep < 0 ? absolutePath : absolutePath.substring(lastSep + 1));
        }

        public FileSystemLocationSnapshot getResult() {
            return builder.getResult();
        }
    }

    private static class DirToVisit {
        final Path path;
        final List<String> relativePathSegments;

        DirToVisit(Path path, List<String> relativePathSegments) {
            this.path = path;
            this.relativePathSegments = relativePathSegments;
        }
    }
}
