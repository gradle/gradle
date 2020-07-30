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
import com.google.common.collect.Lists;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class DirectorySnapshotter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorySnapshotter.class);
    private static final EnumSet<FileVisitOption> DONT_FOLLOW_SYMLINKS = EnumSet.noneOf(FileVisitOption.class);

    private final FileHasher hasher;
    private final Interner<String> stringInterner;
    private final DefaultExcludes defaultExcludes;

    public DirectorySnapshotter(FileHasher hasher, Interner<String> stringInterner, Collection<String> defaultExcludes) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.defaultExcludes = new DefaultExcludes(defaultExcludes);
    }

    public CompleteFileSystemLocationSnapshot snapshot(String absolutePath, @Nullable SnapshottingFilter.DirectoryWalkerPredicate predicate, final AtomicBoolean hasBeenFiltered) {
        try {
            Path rootPath = Paths.get(absolutePath);
            PathVisitor visitor = new PathVisitor(predicate, hasBeenFiltered, hasher, stringInterner, defaultExcludes);
            Files.walkFileTree(rootPath, DONT_FOLLOW_SYMLINKS, Integer.MAX_VALUE, visitor);
            return visitor.getResult();
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", absolutePath), e);
        }
    }

    private static class SymbolicLinkMapping {
        private final String source;
        private final String target;

        private SymbolicLinkMapping(String source, String target) {
            this.source = source;
            this.target = target;
        }

        Optional<String> remapPath(String absolutePath) {
            if (absolutePath.equals(target)) {
                return Optional.of(source);
            }
            if (absolutePath.startsWith(target) && absolutePath.charAt(target.length()) == File.separatorChar) {
                return Optional.of(source + File.separatorChar + absolutePath.substring(target.length() + 1));
            }
            return Optional.empty();
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

    private static class PathVisitor implements java.nio.file.FileVisitor<Path> {
        private final MerkleDirectorySnapshotBuilder builder;
        private final SnapshottingFilter.DirectoryWalkerPredicate predicate;
        private final AtomicBoolean hasBeenFiltered;
        private final FileHasher hasher;
        private final Interner<String> stringInterner;
        private final DefaultExcludes defaultExcludes;
        private final Deque<SymbolicLinkMapping> symbolicLinkMappings = new ArrayDeque<>();
        private final Deque<String> parentDirectories = new ArrayDeque<>();

        public PathVisitor(
            @Nullable SnapshottingFilter.DirectoryWalkerPredicate predicate,
            AtomicBoolean hasBeenFiltered,
            FileHasher hasher,
            Interner<String> stringInterner,
            DefaultExcludes defaultExcludes
        ) {
            this.builder = MerkleDirectorySnapshotBuilder.sortingRequired();
            this.predicate = predicate;
            this.hasBeenFiltered = hasBeenFiltered;
            this.hasher = hasher;
            this.stringInterner = stringInterner;
            this.defaultExcludes = defaultExcludes;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String fileName = getFilename(dir);
            String internedName = intern(fileName);
            if (builder.isRoot() || shouldVisit(dir, internedName, true, builder.getRelativePath())) {
                builder.preVisitDirectory(intern(remapAbsolutePath(dir)), internedName);
                parentDirectories.addFirst(dir.toString());
                return FileVisitResult.CONTINUE;
            } else {
                return FileVisitResult.SKIP_SUBTREE;
            }
        }

        private String getFilename(Path dir) {
            return Optional.ofNullable(dir.getFileName())
                .map(Object::toString)
                .orElse("");
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isSymbolicLink()) {
                BasicFileAttributes targetAttributes = readAttributesOfSymlinkTarget(file, attrs);
                if (targetAttributes.isDirectory()) {
                    try {
                        Path targetDir = file.toRealPath();
                        String targetDirString = targetDir.toString();
                        if (introducesCycle(targetDirString)) {
                            return FileVisitResult.CONTINUE;
                        }
                        symbolicLinkMappings.addFirst(new SymbolicLinkMapping(file.toString(), targetDirString));
                        Files.walkFileTree(targetDir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, this);
                        symbolicLinkMappings.removeFirst();
                    } catch (IOException e) {
                        throw new UncheckedIOException(String.format("Could not list contents of directory '%s'.", file), e);
                    }
                } else {
                    visitResolvedFile(file, targetAttributes, AccessType.VIA_SYMLINK);
                }
            } else {
                visitResolvedFile(file, attrs, AccessType.DIRECT);
            }
            return FileVisitResult.CONTINUE;
        }

        private boolean introducesCycle(String targetDirString) {
            return parentDirectories.contains(targetDirString);
        }

        private String remapAbsolutePath(Path dir) {
            String targetAbsolutePath = dir.toString();
            return symbolicLinkMappings.stream()
                .map(mapping -> mapping.remapPath(targetAbsolutePath))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(targetAbsolutePath);
        }

        private void visitResolvedFile(Path file, BasicFileAttributes targetAttributes, AccessType accessType) {
            String internedName = intern(file.getFileName().toString());
            if (shouldVisit(file, internedName, false, builder.getRelativePath())) {
                builder.visitFile(snapshotFile(file, internedName, targetAttributes, accessType));
            }
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

        private CompleteFileSystemLocationSnapshot snapshotFile(Path absoluteFilePath, String internedName, BasicFileAttributes attrs, AccessType accessType) {
            String internedAbsoluteFilePath = intern(remapAbsolutePath(absoluteFilePath));
            if (attrs.isRegularFile()) {
                try {
                    long lastModified = attrs.lastModifiedTime().toMillis();
                    long fileLength = attrs.size();
                    FileMetadata metadata = DefaultFileMetadata.file(lastModified, fileLength, accessType);
                    HashCode hash = hasher.hash(absoluteFilePath.toFile(), fileLength, lastModified);
                    return new RegularFileSnapshot(internedAbsoluteFilePath, internedName, hash, metadata);
                } catch (UncheckedIOException e) {
                    LOGGER.info("Could not read file path '{}'.", absoluteFilePath, e);
                }
            }
            return new MissingFileSnapshot(internedAbsoluteFilePath, internedName, accessType);
        }

        /** unlistable directories (and maybe some locked files) will stop here */
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            // File loop exceptions are ignored. When we encounter a loop (via symbolic links), we continue
            // so we include all the other files apart from the loop.
            // This way, we include each file only once.
            if (isNotFileSystemLoopException(exc)) {
                String internedName = intern(file.getFileName().toString());
                boolean isDirectory = Files.isDirectory(file);
                if (shouldVisit(file, internedName, isDirectory, builder.getRelativePath())) {
                    LOGGER.info("Could not read file path '{}'.", file);
                    String internedAbsolutePath = intern(file.toString());
                    builder.visitFile(new MissingFileSnapshot(internedAbsolutePath, internedName, AccessType.DIRECT));
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) {
            // File loop exceptions are ignored. When we encounter a loop (via symbolic links), we continue
            // so we include all the other files apart from the loop.
            // This way, we include each file only once.
            if (isNotFileSystemLoopException(exc)) {
                throw new UncheckedIOException(String.format("Could not read directory path '%s'.", dir), exc);
            }
            AccessType accessType = AccessType.viaSymlink(
                !symbolicLinkMappings.isEmpty() && symbolicLinkMappings.getFirst().target.equals(dir.toString())
            );
            builder.postVisitDirectory(accessType);
            parentDirectories.removeFirst();
            return FileVisitResult.CONTINUE;
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
        private boolean shouldVisit(Path path, String internedName, boolean isDirectory, Iterable<String> relativePath) {
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
            boolean allowed = predicate.test(path, internedName, isDirectory, relativePath);
            if (!allowed) {
                hasBeenFiltered.set(true);
            }
            return allowed;
        }

        public CompleteFileSystemLocationSnapshot getResult() {
            return builder.getResult();
        }
    }
}
