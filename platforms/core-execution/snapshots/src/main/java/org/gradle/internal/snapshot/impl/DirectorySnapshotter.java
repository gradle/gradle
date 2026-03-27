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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * For creating {@link DirectorySnapshot}s of directories.
 *
 * <p>Uses two parallelism strategies based on whether a predicate (filter) is present:</p>
 * <ul>
 *   <li><b>No predicate (common case)</b>: fully non-blocking async traversal using
 *       {@link CompletableFuture} composition. Both directory traversal and file hashing
 *       are parallelized. No thread ever blocks waiting for another task, enabling natural
 *       work distribution across the thread pool.</li>
 *   <li><b>With predicate</b>: sequential directory traversal on the main thread
 *       (predicate may not be thread-safe) with parallel file hashing on the executor.</li>
 * </ul>
 */
public class DirectorySnapshotter {
    private static final HashCode DIR_SIGNATURE = Hashing.signature("DIR");

    @VisibleForTesting
    static final int PARALLEL_FILE_THRESHOLD = 5;

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
    private final ExecutorService executor;

    public DirectorySnapshotter(FileHasher hasher, Interner<String> stringInterner, Collection<String> defaultExcludes, DirectorySnapshotterStatistics.Collector collector, ExecutorService executor) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.defaultExcludes = new DefaultExcludes(defaultExcludes);
        this.collector = collector;
        this.executor = executor;
    }

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
            collector.recordVisitFileFailed();
            throw new UncheckedIOException(e);
        }

        FileSystemLocationSnapshot result;
        if (rootAttrs.isDirectory()) {
            if (predicate == null) {
                // Fully async: non-blocking CompletableFuture composition.
                // Only this .get() call blocks (on main thread, not an executor thread).
                try {
                    result = visitor.processDirectoryAsync(rootPath, new HashSet<>()).get().snapshot;
                } catch (ExecutionException e) {
                    throw UncheckedException.throwAsUncheckedException(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            } else {
                // Sequential traversal (predicate not thread-safe) + parallel file hashing
                result = visitor.processDirectory(rootPath, new ArrayList<>(), new HashSet<>()).snapshot;
            }
        } else {
            collector.recordVisitFile();
            String rootName = visitor.getInternedFileName(rootPath);
            result = visitor.processFileAsRoot(rootPath, rootName, rootAttrs);
        }

        if (!hasBeenFiltered.get()) {
            unfilteredSnapshotRecorder.accept(result);
        }
        return result;
    }

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
     * Visits a directory tree. Two modes of operation:
     * <ul>
     *   <li>{@link #processDirectoryAsync}: non-blocking, returns CompletableFuture.
     *       Used when no predicate - enables full tree+file parallelism.</li>
     *   <li>{@link #processDirectory}: synchronous with parallel file hashing.
     *       Used when predicate is present (predicate may not be thread-safe).</li>
     * </ul>
     */
    private static class DirectoryVisitor {
        private final SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate;
        private final AtomicBoolean hasBeenFiltered;
        private final FileHasher hasher;
        private final Interner<String> stringInterner;
        private final DefaultExcludes defaultExcludes;
        private final DirectorySnapshotterStatistics.Collector collector;
        private final SymbolicLinkMapping symbolicLinkMapping;
        private final ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots;
        private final Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder;
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
            ExecutorService executor
        ) {
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

        // ======================== Async path (no predicate) ========================

        /**
         * Non-blocking async directory processing. Lists children on the current thread,
         * then submits file hashing and child directories as CompletableFutures.
         * No thread ever blocks waiting for another task's result.
         */
        CompletableFuture<SnapshotResult> processDirectoryAsync(Path dir, Set<String> parentDirPaths) {
            String internedName = getInternedFileName(dir);
            String internedAbsPath = intern(symbolicLinkMapping.remapAbsolutePath(dir));

            // Previously known snapshot - return immediately
            FileSystemLocationSnapshot previouslyKnownSnapshot = previouslyKnownSnapshots.get(internedAbsPath);
            if (previouslyKnownSnapshot instanceof DirectorySnapshot) {
                return CompletableFuture.completedFuture(new SnapshotResult(previouslyKnownSnapshot, false));
            } else if (previouslyKnownSnapshot != null) {
                throw new IllegalStateException("Expected a previously known directory snapshot at " + internedAbsPath + " but got " + previouslyKnownSnapshot);
            }

            Set<String> newParentDirPaths = new HashSet<>(parentDirPaths);
            newParentDirPaths.add(dir.toString());

            // List children synchronously (I/O work on current thread)
            List<FileToSnapshot> filesToHash = new ArrayList<>();
            List<DirToVisit> dirsToRecurse = new ArrayList<>();
            List<CompletableFuture<DirectorySnapshot>> symlinkDirCFs = new ArrayList<>();

            try (Stream<Path> childStream = listDirectoryChildren(dir)) {
                collector.recordVisitDirectory();

                childStream.forEach(child -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        String childName = getInternedFileName(child);

                        if (attrs.isDirectory()) {
                            if (!defaultExcludes.excludeDir(childName)) {
                                dirsToRecurse.add(new DirToVisit(child));
                            }
                        } else if (attrs.isSymbolicLink()) {
                            collector.recordVisitFile();
                            BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(child, attrs);
                            if (targetAttrs.isDirectory()) {
                                if (!defaultExcludes.excludeDir(childName)) {
                                    CompletableFuture<DirectorySnapshot> symlinkCF = followSymlinkAsync(child, childName, newParentDirPaths);
                                    if (symlinkCF != null) {
                                        symlinkDirCFs.add(symlinkCF);
                                    }
                                }
                            } else if (!defaultExcludes.excludeFile(childName)) {
                                filesToHash.add(new FileToSnapshot(child, childName, targetAttrs, AccessType.VIA_SYMLINK));
                            }
                        } else {
                            collector.recordVisitFile();
                            if (!defaultExcludes.excludeFile(childName)) {
                                filesToHash.add(new FileToSnapshot(child, childName, attrs, AccessType.DIRECT));
                            }
                        }
                    } catch (IOException e) {
                        handleFailedNoFilter(child, e);
                    }
                });
            }

            // Submit file hashing (non-blocking)
            CompletableFuture<List<FileSystemLeafSnapshot>> filesCF = submitFileHashingAsync(filesToHash);

            // Submit child directories (non-blocking) - each returns a CF
            List<CompletableFuture<SnapshotResult>> dirCFs = new ArrayList<>(dirsToRecurse.size());
            for (DirToVisit dirToVisit : dirsToRecurse) {
                dirCFs.add(forkDirectoryAsync(dirToVisit.path, newParentDirPaths));
            }

            // Compose all: when files + dirs + symlinks complete, build the snapshot
            List<CompletableFuture<?>> allWork = new ArrayList<>();
            allWork.add(filesCF);
            allWork.addAll(dirCFs);
            allWork.addAll(symlinkDirCFs);

            return CompletableFuture.allOf(allWork.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> {
                    List<FileSystemLocationSnapshot> children = new ArrayList<>();
                    for (CompletableFuture<SnapshotResult> cf : dirCFs) {
                        children.add(cf.join().snapshot);
                    }
                    for (CompletableFuture<DirectorySnapshot> cf : symlinkDirCFs) {
                        children.add(cf.join());
                    }
                    children.addAll(filesCF.join());
                    return new SnapshotResult(buildDirectorySnapshot(internedAbsPath, internedName, AccessType.DIRECT, children), false);
                });
        }

        /**
         * Forks a directory for async processing on the executor.
         * The executor thread lists the directory and submits its children (quick),
         * then returns. thenCompose flattens the nested CF.
         */
        @SuppressWarnings("FutureReturnValueIgnored") // thenCompose flattens the nested CF
        private CompletableFuture<SnapshotResult> forkDirectoryAsync(Path dir, Set<String> parentDirPaths) {
            return CompletableFuture
                .supplyAsync(() -> processDirectoryAsync(dir, parentDirPaths), executor)
                .thenCompose(Function.identity());
        }

        @Nullable
        private CompletableFuture<DirectorySnapshot> followSymlinkAsync(Path file, String internedFileName, Set<String> parentDirPaths) {
            try {
                Path targetDir = file.toRealPath();
                String targetDirString = targetDir.toString();
                if (parentDirPaths.contains(targetDirString)) {
                    return null;
                }
                SymbolicLinkMapping newMapping = symbolicLinkMapping.withNewMapping(file.toString(), targetDirString, Collections.emptyList());
                DirectoryVisitor subtreeVisitor = new DirectoryVisitor(
                    null, hasBeenFiltered, hasher, stringInterner, defaultExcludes,
                    collector, newMapping, previouslyKnownSnapshots, unfilteredSnapshotRecorder, executor);

                collector.recordVisitHierarchy();
                return subtreeVisitor.processDirectoryAsync(targetDir, parentDirPaths)
                    .thenApply(result -> {
                        DirectorySnapshot targetSnapshot = (DirectorySnapshot) result.snapshot;
                        return new DirectorySnapshot(
                            targetSnapshot.getAbsolutePath(), internedFileName,
                            AccessType.VIA_SYMLINK, targetSnapshot.getHash(), targetSnapshot.getChildren());
                    });
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
            }
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        private CompletableFuture<List<FileSystemLeafSnapshot>> submitFileHashingAsync(List<FileToSnapshot> files) {
            if (files.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            if (files.size() >= PARALLEL_FILE_THRESHOLD) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                CompletableFuture<FileSystemLeafSnapshot>[] futures = new CompletableFuture[files.size()];
                for (int i = 0; i < files.size(); i++) {
                    FileToSnapshot f = files.get(i);
                    futures[i] = CompletableFuture.supplyAsync(
                        () -> snapshotFile(f.path, f.internedName, f.attrs, f.accessType), executor);
                }
                return CompletableFuture.allOf(futures).thenApply(v -> {
                    List<FileSystemLeafSnapshot> results = new ArrayList<>(futures.length);
                    for (CompletableFuture<FileSystemLeafSnapshot> cf : futures) {
                        results.add(cf.join());
                    }
                    return results;
                });
            }
            // Batch: one worker hashes all files
            return CompletableFuture.supplyAsync(() -> {
                List<FileSystemLeafSnapshot> results = new ArrayList<>(files.size());
                for (FileToSnapshot f : files) {
                    results.add(snapshotFile(f.path, f.internedName, f.attrs, f.accessType));
                }
                return results;
            }, executor);
        }

        private void handleFailedNoFilter(Path file, IOException exc) {
            collector.recordVisitFileFailed();
            if (isNotFileSystemLoopException(exc)) {
                String internedFileName = getInternedFileName(file);
                boolean isDirectory = Files.isDirectory(file);
                if (isDirectory ? !defaultExcludes.excludeDir(internedFileName) : !defaultExcludes.excludeFile(internedFileName)) {
                    throw UncheckedException.throwAsUncheckedException(exc);
                }
            }
        }

        // ======================== Sync path (with predicate) ========================

        /**
         * Synchronous directory processing with parallel file hashing.
         * Used when a predicate is present (predicate may not be thread-safe).
         */
        SnapshotResult processDirectory(Path dir, List<String> relativePathSegments, Set<String> parentDirPaths) {
            String internedName = getInternedFileName(dir);
            String internedAbsPath = intern(symbolicLinkMapping.remapAbsolutePath(dir));

            // TODO Reuse previous directory snapshot even when filtering is enabled
            if (predicate == null) {
                FileSystemLocationSnapshot previouslyKnownSnapshot = previouslyKnownSnapshots.get(internedAbsPath);
                if (previouslyKnownSnapshot instanceof DirectorySnapshot) {
                    return new SnapshotResult(previouslyKnownSnapshot, false);
                } else if (previouslyKnownSnapshot != null) {
                    throw new IllegalStateException("Expected a previously known directory snapshot at " + internedAbsPath + " but got " + previouslyKnownSnapshot);
                }
            }

            parentDirPaths.add(dir.toString());

            List<FileToSnapshot> filesToHash = new ArrayList<>();
            List<DirToVisit> dirsToRecurse = new ArrayList<>();
            List<DirectorySnapshot> symlinkDirSnapshots = new ArrayList<>();
            Set<FileSystemLocationSnapshot> filteredChildDirs = new HashSet<>();
            boolean[] isCurrentDirFiltered = {false};

            try (Stream<Path> childStream = listDirectoryChildren(dir)) {
                collector.recordVisitDirectory();

                childStream.forEach(child -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        String childName = getInternedFileName(child);

                        if (attrs.isDirectory()) {
                            Iterable<String> childSegments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                            if (shouldVisit(child, childName, true, childSegments, isCurrentDirFiltered)) {
                                relativePathSegments.add(childName);
                                dirsToRecurse.add(new DirToVisit(child, new ArrayList<>(relativePathSegments)));
                                relativePathSegments.remove(relativePathSegments.size() - 1);
                            }
                        } else if (attrs.isSymbolicLink()) {
                            collector.recordVisitFile();
                            BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(child, attrs);
                            if (targetAttrs.isDirectory()) {
                                DirectorySnapshot symlinkSnapshot = handleSymlinkToDirectory(child, childName, relativePathSegments, parentDirPaths, isCurrentDirFiltered, filteredChildDirs);
                                if (symlinkSnapshot != null) {
                                    symlinkDirSnapshots.add(symlinkSnapshot);
                                }
                            } else {
                                Iterable<String> childSegments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                                if (shouldVisit(child, childName, false, childSegments, isCurrentDirFiltered)) {
                                    filesToHash.add(new FileToSnapshot(child, childName, targetAttrs, AccessType.VIA_SYMLINK));
                                }
                            }
                        } else {
                            collector.recordVisitFile();
                            Iterable<String> childSegments = Iterables.concat(relativePathSegments, Collections.singleton(childName));
                            if (shouldVisit(child, childName, false, childSegments, isCurrentDirFiltered)) {
                                filesToHash.add(new FileToSnapshot(child, childName, attrs, AccessType.DIRECT));
                            }
                        }
                    } catch (IOException e) {
                        handleFailed(child, e, relativePathSegments, isCurrentDirFiltered);
                    }
                });
            }

            // Submit file hashing, recurse into subdirectories, then join
            CompletableFuture<List<FileSystemLeafSnapshot>> filesCF = submitFileHashingAsync(filesToHash);

            List<FileSystemLocationSnapshot> children = new ArrayList<>();
            for (DirToVisit dirToVisit : dirsToRecurse) {
                SnapshotResult childResult = processDirectory(dirToVisit.path, dirToVisit.relativePathSegments, parentDirPaths);
                children.add(childResult.snapshot);
                if (childResult.isFiltered) {
                    filteredChildDirs.add(childResult.snapshot);
                    isCurrentDirFiltered[0] = true;
                }
            }
            children.addAll(symlinkDirSnapshots);
            try {
                children.addAll(filesCF.join());
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }

            parentDirPaths.remove(dir.toString());

            DirectorySnapshot result = buildDirectorySnapshot(internedAbsPath, internedName, AccessType.DIRECT, children);

            if (isCurrentDirFiltered[0]) {
                for (FileSystemLocationSnapshot child : result.getChildren()) {
                    if (child.getType() != FileType.Directory || !filteredChildDirs.contains(child)) {
                        unfilteredSnapshotRecorder.accept(child);
                    }
                }
            }

            return new SnapshotResult(result, isCurrentDirFiltered[0]);
        }

        FileSystemLocationSnapshot processFileAsRoot(Path file, String internedName, BasicFileAttributes attrs) {
            if (attrs.isSymbolicLink()) {
                BasicFileAttributes targetAttrs = readAttributesOfSymlinkTarget(file, attrs);
                if (targetAttrs.isDirectory()) {
                    AtomicBoolean symlinkHasBeenFiltered = new AtomicBoolean();
                    DirectorySnapshot targetSnapshot = followSymlinkSync(file, symlinkHasBeenFiltered);
                    if (targetSnapshot != null) {
                        DirectorySnapshot snapshotViaSymlink = new DirectorySnapshot(
                            targetSnapshot.getAbsolutePath(), internedName,
                            AccessType.VIA_SYMLINK, targetSnapshot.getHash(), targetSnapshot.getChildren()
                        );
                        if (symlinkHasBeenFiltered.get()) {
                            hasBeenFiltered.set(true);
                        }
                        return snapshotViaSymlink;
                    }
                    return new MissingFileSnapshot(intern(symbolicLinkMapping.remapAbsolutePath(file)), internedName, AccessType.VIA_SYMLINK);
                } else {
                    return snapshotFile(file, internedName, targetAttrs, AccessType.VIA_SYMLINK);
                }
            } else {
                return snapshotFile(file, internedName, attrs, AccessType.DIRECT);
            }
        }

        @Nullable
        private DirectorySnapshot handleSymlinkToDirectory(Path symlinkPath, String internedFileName, List<String> parentRelativeSegments, Set<String> parentDirPaths, boolean[] isCurrentDirFiltered, Set<FileSystemLocationSnapshot> filteredChildDirs) {
            Iterable<String> symlinkSegments = Iterables.concat(parentRelativeSegments, Collections.singleton(internedFileName));
            if (!shouldVisit(symlinkPath, internedFileName, true, symlinkSegments, isCurrentDirFiltered)) {
                return null;
            }

            AtomicBoolean symlinkHasBeenFiltered = new AtomicBoolean();
            DirectorySnapshot targetSnapshot = followSymlinkWithPredicate(symlinkPath, internedFileName, symlinkHasBeenFiltered, parentRelativeSegments, parentDirPaths);
            if (targetSnapshot == null) {
                return null;
            }

            DirectorySnapshot snapshotViaSymlink = new DirectorySnapshot(
                targetSnapshot.getAbsolutePath(),
                internedFileName,
                AccessType.VIA_SYMLINK,
                targetSnapshot.getHash(),
                targetSnapshot.getChildren()
            );
            if (symlinkHasBeenFiltered.get()) {
                isCurrentDirFiltered[0] = true;
                hasBeenFiltered.set(true);
                filteredChildDirs.add(snapshotViaSymlink);
            }
            return snapshotViaSymlink;
        }

        @Nullable
        private DirectorySnapshot followSymlinkSync(Path file, AtomicBoolean symlinkHasBeenFiltered) {
            try {
                Path targetDir = file.toRealPath();
                String targetDirString = targetDir.toString();
                SymbolicLinkMapping newMapping = symbolicLinkMapping.withNewMapping(file.toString(), targetDirString, Collections.emptyList());

                DirectoryVisitor subtreeVisitor = new DirectoryVisitor(
                    predicate, symlinkHasBeenFiltered, hasher, stringInterner, defaultExcludes,
                    collector, newMapping, previouslyKnownSnapshots, unfilteredSnapshotRecorder, executor);

                collector.recordVisitHierarchy();
                SnapshotResult result = subtreeVisitor.processDirectory(targetDir, new ArrayList<>(), new HashSet<>());
                return (DirectorySnapshot) result.snapshot;
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
            }
        }

        @Nullable
        private DirectorySnapshot followSymlinkWithPredicate(Path file, String internedFileName, AtomicBoolean symlinkHasBeenFiltered, List<String> parentRelativeSegments, Set<String> parentDirPaths) {
            try {
                Path targetDir = file.toRealPath();
                String targetDirString = targetDir.toString();
                if (!parentDirPaths.contains(targetDirString)) {
                    Iterable<String> symlinkSegments = Iterables.concat(parentRelativeSegments, Collections.singleton(internedFileName));
                    SymbolicLinkMapping newMapping = symbolicLinkMapping.withNewMapping(file.toString(), targetDirString, symlinkSegments);

                    DirectoryVisitor subtreeVisitor = new DirectoryVisitor(
                        predicate, symlinkHasBeenFiltered, hasher, stringInterner, defaultExcludes,
                        collector, newMapping, previouslyKnownSnapshots, unfilteredSnapshotRecorder, executor);

                    collector.recordVisitHierarchy();
                    SnapshotResult result = subtreeVisitor.processDirectory(targetDir, new ArrayList<>(), parentDirPaths);
                    return (DirectorySnapshot) result.snapshot;
                } else {
                    return null;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
            }
        }

        // ======================== Shared utilities ========================

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

        private void handleFailed(Path file, IOException exc, List<String> parentRelativeSegments, boolean[] isCurrentDirFiltered) {
            collector.recordVisitFileFailed();
            if (isNotFileSystemLoopException(exc)) {
                String internedFileName = getInternedFileName(file);
                boolean isDirectory = Files.isDirectory(file);
                Iterable<String> segments = Iterables.concat(parentRelativeSegments, Collections.singleton(internedFileName));
                if (shouldVisit(file, internedFileName, isDirectory, segments, isCurrentDirFiltered)) {
                    throw UncheckedException.throwAsUncheckedException(exc);
                }
            }
        }

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
                return symlinkAttributes;
            }
        }

        private static boolean isNotFileSystemLoopException(@Nullable IOException e) {
            return e != null && !(e instanceof FileSystemLoopException);
        }

        private String intern(String string) {
            return stringInterner.intern(string);
        }

        private boolean shouldVisit(Path path, String internedName, boolean isDirectory, Iterable<String> segments, boolean[] isCurrentDirFiltered) {
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
                isCurrentDirFiltered[0] = true;
                hasBeenFiltered.set(true);
            }
            return allowed;
        }

        String getInternedFileName(Path path) {
            String absolutePath = path.toString();
            int lastSep = absolutePath.lastIndexOf(File.separatorChar);
            return intern(lastSep < 0 ? absolutePath : absolutePath.substring(lastSep + 1));
        }
    }

    private static class SnapshotResult {
        final FileSystemLocationSnapshot snapshot;
        final boolean isFiltered;

        SnapshotResult(FileSystemLocationSnapshot snapshot, boolean isFiltered) {
            this.snapshot = snapshot;
            this.isFiltered = isFiltered;
        }
    }

    private static class DirToVisit {
        final Path path;
        @Nullable
        final List<String> relativePathSegments;

        DirToVisit(Path path) {
            this.path = path;
            this.relativePathSegments = null;
        }

        DirToVisit(Path path, List<String> relativePathSegments) {
            this.path = path;
            this.relativePathSegments = relativePathSegments;
        }
    }
}
