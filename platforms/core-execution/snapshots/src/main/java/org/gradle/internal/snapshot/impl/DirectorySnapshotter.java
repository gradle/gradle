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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS;

/**
 * For creating {@link DirectorySnapshot}s of directories.
 */
public class DirectorySnapshotter {
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

    public DirectorySnapshotter(FileHasher hasher, Interner<String> stringInterner, Collection<String> defaultExcludes, DirectorySnapshotterStatistics.Collector collector) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.defaultExcludes = new DefaultExcludes(defaultExcludes);
        this.collector = collector;
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
        Set<FileSystemLocationSnapshot> filteredDirectorySnapshots = ConcurrentHashMap.newKeySet();
        collector.recordVisitHierarchy();
        ParallelPathVisitor rootVisitor = new ParallelPathVisitor(rootPath, Collections.emptyList(), Collections.emptySet(), predicate, hasBeenFiltered, hasher, stringInterner, defaultExcludes,
            collector, EMPTY_SYMBOLIC_LINK_MAPPING, previouslyKnownSnapshots, unfilteredSnapshotRecorder, filteredDirectorySnapshots);
        ForkJoinPool p = new ForkJoinPool();
        try {
            FileSystemLocationSnapshot result = p.invoke(rootVisitor);
            if (!hasBeenFiltered.get()) {
                unfilteredSnapshotRecorder.accept(result);
            }
            return result;
        } finally {
            p.shutdown();
        }
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

    private static class ParallelPathVisitor extends RecursiveTask<FileSystemLocationSnapshot> {
        private final Path currentDir;
        private final List<String> relativePathSegments;
        private final Set<Path> additionalParentDirectories; // track indirect parents when following symlinks
        private final SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate;
        private final AtomicBoolean hasBeenFiltered;
        private final FileHasher hasher;
        private final Interner<String> stringInterner;
        private final DefaultExcludes defaultExcludes;
        private final DirectorySnapshotterStatistics.Collector collector;
        private final SymbolicLinkMapping symbolicLinkMapping;
        private final Set<FileSystemLocationSnapshot> filteredDirectorySnapshots;
        private final ImmutableMap<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots;
        private final Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder;
        private final FilteredTrackingMerkleDirectorySnapshotBuilder builder;

        public ParallelPathVisitor(
            Path currentDir,
            List<String> relativePathSegments,
            Set<Path> additionalParentDirectories,
            SnapshottingFilter.@Nullable DirectoryWalkerPredicate predicate,
            AtomicBoolean hasBeenFiltered,
            FileHasher hasher,
            Interner<String> stringInterner,
            DefaultExcludes defaultExcludes,
            DirectorySnapshotterStatistics.Collector statisticsCollector,
            SymbolicLinkMapping symbolicLinkMapping,
            Map<String, ? extends FileSystemLocationSnapshot> previouslyKnownSnapshots,
            Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder,
            Set<FileSystemLocationSnapshot> filteredDirectorySnapshots
        ) {
            this.currentDir = currentDir;
            this.relativePathSegments = relativePathSegments;
            this.additionalParentDirectories = additionalParentDirectories;
            this.predicate = predicate;
            this.hasBeenFiltered = hasBeenFiltered;
            this.hasher = hasher;
            this.stringInterner = stringInterner;
            this.defaultExcludes = defaultExcludes;
            this.collector = statisticsCollector;
            this.symbolicLinkMapping = symbolicLinkMapping;
            this.previouslyKnownSnapshots = ImmutableMap.copyOf(previouslyKnownSnapshots);
            this.unfilteredSnapshotRecorder = unfilteredSnapshotRecorder;
            this.filteredDirectorySnapshots = filteredDirectorySnapshots;
            this.builder = FilteredTrackingMerkleDirectorySnapshotBuilder.sortingRequired(snapshot -> recordUnfilteredSnapshot(snapshot, unfilteredSnapshotRecorder));
        }

        private void recordUnfilteredSnapshot(FileSystemLocationSnapshot snapshot, Consumer<FileSystemLocationSnapshot> unfilteredSnapshotRecorder) {
            if (snapshot.getType() != FileType.Directory || !filteredDirectorySnapshots.contains(snapshot)) {
                unfilteredSnapshotRecorder.accept(snapshot);
            }
        }

        @Override
        protected FileSystemLocationSnapshot compute() {
            String internedRemappedAbsolutePath = intern(symbolicLinkMapping.remapAbsolutePath(currentDir));

            // TODO Reuse previous directory snapshot even when filtering is enabled
            if (predicate == null) {
                FileSystemLocationSnapshot previouslyKnownSnapshot = previouslyKnownSnapshots.get(internedRemappedAbsolutePath);
                if (previouslyKnownSnapshot instanceof DirectorySnapshot) {
                    return (DirectorySnapshot) previouslyKnownSnapshot;
                } else if (previouslyKnownSnapshot != null) {
                    throw new IllegalStateException("Expected a previously known directory snapshot at " + internedRemappedAbsolutePath + " but got " + previouslyKnownSnapshot);
                }
            }

            try {
                BasicFileAttributes currentDirAttr = Files.readAttributes(currentDir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (currentDirAttr.isDirectory()) {
                    builder.enterDirectory(AccessType.DIRECT, internedRemappedAbsolutePath, getInternedFileName(currentDir), INCLUDE_EMPTY_DIRS);

                    List<ParallelPathVisitor> childVisitors = new ArrayList<>();

                    try (Stream<Path> children = Files.list(currentDir)) {
                        collector.recordVisitDirectory();
                        children.forEach(child -> {
                            try {
                                BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                                if (attrs.isDirectory()) {
                                    if (shouldVisitDirectory(child, getInternedFileName(child), false)) {
                                        childVisitors.add(visitSubDirectory(child));
                                    }
                                } else {
                                    doVisitFile(child, attrs, false);
                                }
                            } catch (IOException e) {
                                doFailed(child, e);
                            }
                        });
                    }

                    childVisitors.stream().map(ForkJoinTask::join).map(DirectorySnapshot.class::cast).forEach(builder::visitDirectory);

                    boolean currentLevelComplete = builder.isCurrentLevelUnfiltered();
                    FileSystemLocationSnapshot currentLevel = builder.leaveDirectory();
                    if (!currentLevelComplete) {
                        filteredDirectorySnapshots.add(currentLevel);
                    }
                } else {
                    doVisitFile(currentDir, currentDirAttr, true);
                }
            } catch (IOException e) {
                doFailed(currentDir, e);
            }

            return builder.getResult();
        }

        private ParallelPathVisitor visitSubDirectory(Path dir) {
            List<String> newRelativePathSegments = new ArrayList<>(relativePathSegments);
            newRelativePathSegments.add(getInternedFileName(dir));
            ParallelPathVisitor subDirVisitor = new ParallelPathVisitor(dir, newRelativePathSegments, Collections.emptySet(), predicate, hasBeenFiltered, hasher, stringInterner,
                defaultExcludes, collector, symbolicLinkMapping, previouslyKnownSnapshots, unfilteredSnapshotRecorder, filteredDirectorySnapshots);
            subDirVisitor.fork();
            return subDirVisitor;
        }

        /**
         * Can be the 'root', if the root itself is a symlink.
         */
        protected void doVisitFile(Path file, BasicFileAttributes attrs, boolean isRoot) {
            collector.recordVisitFile();
            String internedFileName = getInternedFileName(file);
            if (attrs.isSymbolicLink()) {
                BasicFileAttributes targetAttributes = readAttributesOfSymlinkTarget(file, attrs);
                if (targetAttributes.isDirectory()) {
                    AtomicBoolean symlinkHasBeenFiltered = new AtomicBoolean();
                    DirectorySnapshot targetSnapshot = followSymlink(file, internedFileName, symlinkHasBeenFiltered, isRoot);
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
                } else {
                    visitResolvedFile(file, internedFileName, targetAttributes, AccessType.VIA_SYMLINK);
                }
            } else {
                visitResolvedFile(file, internedFileName, attrs, AccessType.DIRECT);
            }
        }

        @Nullable
        private DirectorySnapshot followSymlink(Path file, String internedFileName, AtomicBoolean symlinkHasBeenFiltered, boolean isRoot) {
            try {
                Path targetDir = file.toRealPath();
                String targetDirString = targetDir.toString();
                if (!introducesCycle(targetDir) && shouldVisitDirectory(targetDir, internedFileName, isRoot)) {
                    Set<Path> newParentDirectories = new HashSet<>(additionalParentDirectories);
                    newParentDirectories.add(currentDir);
                    ParallelPathVisitor subtreeVisitor = new ParallelPathVisitor(
                        targetDir,
                        Collections.emptyList(),
                        newParentDirectories,
                        predicate,
                        symlinkHasBeenFiltered,
                        hasher,
                        stringInterner,
                        defaultExcludes,
                        collector,
                        symbolicLinkMapping.withNewMapping(file.toString(), targetDirString, isRoot ? Collections.emptyList() : Iterables.concat(relativePathSegments, Collections.singleton(internedFileName))),
                        previouslyKnownSnapshots,
                        unfilteredSnapshotRecorder, filteredDirectorySnapshots);

                    collector.recordVisitHierarchy();
                    return (DirectorySnapshot) subtreeVisitor.invoke();
                } else {
                    return null;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
            }
        }

        private boolean introducesCycle(Path targetDir) {
            return currentDir.startsWith(targetDir) || additionalParentDirectories.stream().anyMatch(p -> p.startsWith(targetDir));
        }

        private void visitResolvedFile(Path file, String internedName, BasicFileAttributes targetAttributes, AccessType accessType) {
            if (shouldVisitFile(file, internedName)) {
                builder.visitLeafElement(snapshotFile(file, internedName, targetAttributes, accessType));
            }
        }

        private boolean shouldVisitDirectory(Path dir, String internedName, boolean isRoot) {
            return isRoot || shouldVisit(dir, internedName, true);
        }

        private boolean shouldVisitFile(Path file, String internedName) {
            return shouldVisit(file, internedName, false);
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
         * unlistable directories (and maybe some locked files) will stop here
         */
        protected void doFailed(Path file, IOException exc) {
            collector.recordVisitFileFailed();
            String internedFileName = getInternedFileName(file);
            // File loop exceptions are ignored. When we encounter a loop (via symbolic links), we continue,
            // so we include all the other files apart from the loop.
            // This way, we include each file only once.
            if (isNotFileSystemLoopException(exc)) {
                boolean isDirectory = Files.isDirectory(file);
                if (shouldVisit(file, internedFileName, isDirectory)) {
                    throw UncheckedException.throwAsUncheckedException(exc);
                }
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
        private boolean shouldVisit(Path path, String internedName, boolean isDirectory) {
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
            boolean allowed = predicate.test(path, internedName, isDirectory, symbolicLinkMapping.getRemappedSegments(Iterables.concat(relativePathSegments, Collections.singleton(internedName))));
            if (!allowed) {
                builder.markCurrentLevelAsFiltered();
                hasBeenFiltered.set(true);
            }
            return allowed;
        }

        private String getInternedFileName(Path path) {
            // Path also has getFileName() but it creates additional allocations,
            // and since this is on a hot path we optimized it
            String absolutePath = path.toString();
            int lastSep = absolutePath.lastIndexOf(File.separatorChar);
            return intern(lastSep < 0 ? absolutePath : absolutePath.substring(lastSep + 1));
        }
    }
}
