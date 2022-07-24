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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.SnapshottingFilter;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS;

/**
 * For creating {@link DirectorySnapshot}s of directories.
 */
public class DirectorySnapshotter {
    private static final EnumSet<FileVisitOption> DONT_FOLLOW_SYMLINKS = EnumSet.noneOf(FileVisitOption.class);
    private static final SymbolicLinkMapping EMPTY_SYMBOLIC_LINK_MAPPING = new SymbolicLinkMapping() {

        @Override
        public String remapAbsolutePath(Path path) {
            return path.toString();
        }

        @Override
        public SymbolicLinkMapping withNewMapping(String source, String target, RelativePathTracker currentPathTracker) {
            return new DefaultSymbolicLinkMapping(source, target, currentPathTracker.getSegments());
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
     * Snapshots a directory.
     *
     * Follows symlinks and includes them in the returned snapshot.
     * Snapshots of followed symlinks are marked with {@link AccessType#VIA_SYMLINK}.
     *
     * @param absolutePath The absolute path of the directory to snapshot.
     * @param predicate A predicate that determines which files to include in the snapshot.
     *                  {@code null} means to include everything.
     * @param unfilteredSnapshotConsumer If the returned snapshot is filtered by the predicate, i.e. it doesn't have all the contents of the directory,
     *                                   then this consumer will receive all the unfiltered snapshots within the snapshot directory.
     *                                   For example, if an element of a directory is filtered out, the consumer will receive all the non-filtered out
     *                                   file snapshots and all the non-filtered directory snapshots in the directory.
     *
     * @return The (possible filtered) snapshot of the directory.
     */
    public FileSystemLocationSnapshot snapshot(String absolutePath, @Nullable SnapshottingFilter.DirectoryWalkerPredicate predicate, Consumer<FileSystemLocationSnapshot> unfilteredSnapshotConsumer) {
        try {
            AtomicBoolean hasBeenFiltered = new AtomicBoolean();
            Path rootPath = Paths.get(absolutePath);
            PathVisitor visitor = new PathVisitor(predicate, hasBeenFiltered, hasher, stringInterner, defaultExcludes, collector, EMPTY_SYMBOLIC_LINK_MAPPING, unfilteredSnapshotConsumer);
            Files.walkFileTree(rootPath, DONT_FOLLOW_SYMLINKS, Integer.MAX_VALUE, visitor);
            FileSystemLocationSnapshot result = visitor.getResult();
            if (!hasBeenFiltered.get()) {
                unfilteredSnapshotConsumer.accept(result);
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", absolutePath), e);
        }
    }

    private interface SymbolicLinkMapping {
        String remapAbsolutePath(Path path);
        @CheckReturnValue
        SymbolicLinkMapping withNewMapping(String source, String target, RelativePathTracker currentPathTracker);
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
        public SymbolicLinkMapping withNewMapping(String source, String target, RelativePathTracker currentPathTracker) {
            return new DefaultSymbolicLinkMapping(remapAbsolutePath(source), target, getRemappedSegments(currentPathTracker.getSegments()));
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
            final List<String> excludeFiles = Lists.newArrayList();
            final List<String> excludeDirs = Lists.newArrayList();
            final List<Predicate<String>> excludeFileSpecs = Lists.newArrayList();
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

    private static class PathVisitor extends DirectorySnapshotterStatistics.CollectingFileVisitor {
        private final RelativePathTracker pathTracker = new RelativePathTracker();
        private final FilteredTrackingMerkleDirectorySnapshotBuilder builder;
        private final SnapshottingFilter.DirectoryWalkerPredicate predicate;
        private final AtomicBoolean hasBeenFiltered;
        private final FileHasher hasher;
        private final Interner<String> stringInterner;
        private final DefaultExcludes defaultExcludes;
        private final SymbolicLinkMapping symbolicLinkMapping;
        private final Deque<String> parentDirectories = new ArrayDeque<>();
        private final Set<FileSystemLocationSnapshot> filteredDirectorySnapshots = new HashSet<>();
        private final Consumer<FileSystemLocationSnapshot> unfilteredSnapshotConsumer;

        public PathVisitor(
            @Nullable SnapshottingFilter.DirectoryWalkerPredicate predicate,
            AtomicBoolean hasBeenFiltered,
            FileHasher hasher,
            Interner<String> stringInterner,
            DefaultExcludes defaultExcludes,
            DirectorySnapshotterStatistics.Collector statisticsCollector,
            SymbolicLinkMapping symbolicLinkMapping,
            Consumer<FileSystemLocationSnapshot> unfilteredSnapshotConsumer
        ) {
            super(statisticsCollector);
            this.builder = FilteredTrackingMerkleDirectorySnapshotBuilder.sortingRequired(this::consumeUnfilteredSnapshot);
            this.predicate = predicate;
            this.hasBeenFiltered = hasBeenFiltered;
            this.hasher = hasher;
            this.stringInterner = stringInterner;
            this.defaultExcludes = defaultExcludes;
            this.symbolicLinkMapping = symbolicLinkMapping;
            this.unfilteredSnapshotConsumer = unfilteredSnapshotConsumer;
        }

        private void consumeUnfilteredSnapshot(FileSystemLocationSnapshot snapshot) {
            if (snapshot.getType() == FileType.Directory) {
                if (!filteredDirectorySnapshots.contains(snapshot)) {
                    unfilteredSnapshotConsumer.accept(snapshot);
                }
            } else {
                unfilteredSnapshotConsumer.accept(snapshot);
            }
        }

        @Override
        protected FileVisitResult doPreVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String fileName = getInternedFileName(dir);
            pathTracker.enter(fileName);
            if (shouldVisitDirectory(dir, fileName)) {
                builder.enterDirectory(AccessType.DIRECT, intern(symbolicLinkMapping.remapAbsolutePath(dir)), fileName, INCLUDE_EMPTY_DIRS);
                parentDirectories.addFirst(dir.toString());
                return FileVisitResult.CONTINUE;
            } else {
                pathTracker.leave();
                return FileVisitResult.SKIP_SUBTREE;
            }
        }

        @Override
        protected FileVisitResult doPostVisitDirectory(Path dir, IOException exc) {
            pathTracker.leave();
            // File loop exceptions are ignored. When we encounter a loop (via symbolic links), we continue
            // so we include all the other files apart from the loop.
            // This way, we include each file only once.
            if (isNotFileSystemLoopException(exc)) {
                throw new UncheckedIOException(String.format("Could not read directory path '%s'.", dir), exc);
            }
            boolean currentLevelComplete = builder.isCurrentLevelUnfiltered();
            FileSystemLocationSnapshot currentLevel = builder.leaveDirectory();
            if (!currentLevelComplete) {
                filteredDirectorySnapshots.add(currentLevel);
            }
            parentDirectories.removeFirst();
            return FileVisitResult.CONTINUE;
        }

        @Override
        protected FileVisitResult doVisitFile(Path file, BasicFileAttributes attrs) {
            String internedFileName = getInternedFileName(file);
            pathTracker.enter(internedFileName);
            try {
                if (attrs.isSymbolicLink()) {
                    BasicFileAttributes targetAttributes = readAttributesOfSymlinkTarget(file, attrs);
                    if (targetAttributes.isDirectory()) {
                        AtomicBoolean symlinkHasBeenFiltered = new AtomicBoolean(false);
                        DirectorySnapshot targetSnapshot = followSymlink(file, internedFileName, symlinkHasBeenFiltered);
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
                        visitResolvedFile(file, targetAttributes, AccessType.VIA_SYMLINK);
                    }
                } else {
                    visitResolvedFile(file, attrs, AccessType.DIRECT);
                }
                return FileVisitResult.CONTINUE;
            } finally {
                pathTracker.leave();
            }
        }

        @Nullable
        private DirectorySnapshot followSymlink(Path file, String internedFileName, AtomicBoolean symlinkHasBeenFiltered) {
            try {
                Path targetDir = file.toRealPath();
                String targetDirString = targetDir.toString();
                if (!introducesCycle(targetDirString) && shouldVisitDirectory(targetDir, internedFileName)) {
                    PathVisitor subtreeVisitor = new PathVisitor(
                        predicate,
                        symlinkHasBeenFiltered,
                        hasher,
                        stringInterner,
                        defaultExcludes,
                        collector,
                        symbolicLinkMapping.withNewMapping(file.toString(), targetDirString, pathTracker),
                        unfilteredSnapshotConsumer);
                    Files.walkFileTree(targetDir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, subtreeVisitor);
                    return (DirectorySnapshot) subtreeVisitor.getResult();
                } else {
                    return null;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
            }
        }

        private boolean introducesCycle(String targetDirString) {
            return parentDirectories.contains(targetDirString);
        }

        private void visitResolvedFile(Path file, BasicFileAttributes targetAttributes, AccessType accessType) {
            String internedName = intern(file.getFileName().toString());
            if (shouldVisitFile(file, internedName)) {
                builder.visitLeafElement(snapshotFile(file, internedName, targetAttributes, accessType));
            }
        }

        private boolean shouldVisitDirectory(Path dir, String internedName) {
            return pathTracker.isRoot() || shouldVisit(dir, internedName, true);
        }

        private boolean shouldVisitFile(Path file, String internedName) {
            return shouldVisit(file, internedName, false);
        }

        private BasicFileAttributes readAttributesOfSymlinkTarget(Path symlink, BasicFileAttributes symlinkAttributes) {
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
            if (attrs.isSymbolicLink()) {
                return new MissingFileSnapshot(internedRemappedAbsoluteFilePath, internedName, accessType);
            } else if (!attrs.isRegularFile()) {
                throw new UncheckedIOException(new IOException(String.format("Cannot snapshot %s: not a regular file", internedRemappedAbsoluteFilePath)));
            }
            long lastModified = attrs.lastModifiedTime().toMillis();
            long fileLength = attrs.size();
            FileMetadata metadata = DefaultFileMetadata.file(lastModified, fileLength, accessType);
            HashCode hash = hasher.hash(absoluteFilePath.toFile(), fileLength, lastModified);
            return new RegularFileSnapshot(internedRemappedAbsoluteFilePath, internedName, hash, metadata);
        }

        /** unlistable directories (and maybe some locked files) will stop here */
        @Override
        protected FileVisitResult doVisitFileFailed(Path file, IOException exc) {
            String internedFileName = getInternedFileName(file);
            pathTracker.enter(internedFileName);
            try {
                // File loop exceptions are ignored. When we encounter a loop (via symbolic links), we continue
                // so we include all the other files apart from the loop.
                // This way, we include each file only once.
                if (isNotFileSystemLoopException(exc)) {
                    boolean isDirectory = Files.isDirectory(file);
                    if (shouldVisit(file, internedFileName, isDirectory)) {
                        throw new UncheckedIOException(exc);
                    }
                }
                return FileVisitResult.CONTINUE;
            } finally {
                pathTracker.leave();
            }
        }

        private boolean isNotFileSystemLoopException(@Nullable IOException e) {
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
            boolean allowed = predicate.test(path, internedName, isDirectory, symbolicLinkMapping.getRemappedSegments(pathTracker.getSegments()));
            if (!allowed) {
                builder.markCurrentLevelAsFiltered();
                hasBeenFiltered.set(true);
            }
            return allowed;
        }

        private String getInternedFileName(Path dir) {
            Path fileName = dir.getFileName();
            return fileName == null ? "" : intern(fileName.toString());
        }

        public FileSystemLocationSnapshot getResult() {
            return builder.getResult();
        }
    }
}
