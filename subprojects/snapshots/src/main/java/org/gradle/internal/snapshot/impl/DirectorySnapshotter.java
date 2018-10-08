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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.nativeintegration.filesystem.DefaultFileMetadata;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.Stat;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;

public class DirectorySnapshotter {
    private final FileHasher hasher;
    private final FileSystem fileSystem;
    private final StringInterner stringInterner;
    private final DefaultExcludes defaultExcludes;

    public DirectorySnapshotter(FileHasher hasher, FileSystem fileSystem, StringInterner stringInterner, String... defaultExcludes) {
        this.hasher = hasher;
        this.fileSystem = fileSystem;
        this.stringInterner = stringInterner;
        this.defaultExcludes = new DefaultExcludes(defaultExcludes);
    }

    public FileSystemLocationSnapshot snapshot(String absolutePath, @Nullable PatternSet patterns, final MutableBoolean hasBeenFiltered) {
        Path rootPath = Paths.get(absolutePath);
        final Spec<FileTreeElement> spec = (patterns == null || patterns.isEmpty()) ? null : patterns.getAsSpec();
        final MerkleDirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.sortingRequired();

        try {
            Files.walkFileTree(rootPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new java.nio.file.FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = stringInterner.intern(dir.getFileName().toString());
                    if (builder.isRoot() || isAllowed(dir, name, true, attrs, builder.getRelativePath())) {
                        builder.preVisitDirectory(internedAbsolutePath(dir), name);
                        return FileVisitResult.CONTINUE;
                    } else {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                @Override
                public FileVisitResult visitFile(Path file, @Nullable BasicFileAttributes attrs) {
                    String name = stringInterner.intern(file.getFileName().toString());
                    if (isAllowed(file, name, false, attrs, builder.getRelativePath())) {
                        if (attrs == null) {
                            throw new GradleException(String.format("Cannot read file '%s': not authorized.", file));
                        }
                        if (attrs.isSymbolicLink()) {
                            // when FileVisitOption.FOLLOW_LINKS, we only get here when link couldn't be followed
                            throw new GradleException(String.format("Could not list contents of '%s'. Couldn't follow symbolic link.", file));
                        }
                        addFileSnapshot(file, name, attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // File loop exceptions are ignored. When we encounter a loop (via symbolic links), we continue
                    // so we include all the other files apart from the loop.
                    // This way, we include each file only once.
                    if (isNotFileSystemLoopException(exc) && isAllowed(file, file.getFileName().toString(), false, null, builder.getRelativePath())) {
                        throw new GradleException(String.format("Could not read path '%s'.", file), exc);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) {
                    // File loop exceptions are ignored. When we encounter a loop (via symbolic links), we continue
                    // so we include all the other files apart from the loop.
                    // This way, we include each file only once.
                    if (isNotFileSystemLoopException(exc)) {
                        throw new GradleException(String.format("Could not read directory path '%s'.", dir), exc);
                    }
                    builder.postVisitDirectory();
                    return FileVisitResult.CONTINUE;
                }

                private boolean isNotFileSystemLoopException(@Nullable IOException e) {
                    return e != null && !(e instanceof FileSystemLoopException);
                }

                private void addFileSnapshot(Path file, String name, BasicFileAttributes attrs) {
                    Preconditions.checkNotNull(attrs, "Unauthorized access to %", file);
                    DefaultFileMetadata metadata = new DefaultFileMetadata(FileType.RegularFile, attrs.lastModifiedTime().toMillis(), attrs.size());
                    HashCode hash = hasher.hash(file.toFile(), metadata);
                    RegularFileSnapshot fileSnapshot = new RegularFileSnapshot(internedAbsolutePath(file), name, hash, metadata.getLastModified());
                    builder.visit(fileSnapshot);
                }

                private String internedAbsolutePath(Path file) {
                    return stringInterner.intern(file.toString());
                }

                private boolean isAllowed(Path path, String name, boolean isDirectory, @Nullable BasicFileAttributes attrs, Iterable<String> relativePath) {
                    if (isDirectory) {
                        if (defaultExcludes.excludeDir(name)) {
                            return false;
                        }
                    } else if (defaultExcludes.excludeFile(name)) {
                        return false;
                    }
                    if (spec == null) {
                        return true;
                    }
                    boolean allowed = spec.isSatisfiedBy(new PathBackedFileTreeElement(path, name, isDirectory, attrs, relativePath, fileSystem));
                    if (!allowed) {
                        hasBeenFiltered.set(true);
                    }
                    return allowed;
                }
            });
        } catch (IOException e) {
            throw new GradleException(String.format("Could not list contents of directory '%s'.", rootPath), e);
        }
        return builder.getResult();
    }

    @VisibleForTesting
    static class DefaultExcludes {
        private final ImmutableSet<String> excludeFileNames;
        private final ImmutableSet<String> excludedDirNames;
        private final Predicate<String> excludedFileNameSpec;

        public DefaultExcludes(String[] defaultExcludes) {
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
                        Predicate<String> start = firstStar == 0 ? Predicates.<String>alwaysTrue() : new StartMatcher(defaultExclude.substring(0, firstStar));
                        Predicate<String> end = firstStar == length - 1 ? Predicates.<String>alwaysTrue() : new EndMatcher(defaultExclude.substring(firstStar + 1, length));
                        excludeFileSpecs.add(Predicates.and(start, end));
                    }
                }
            }

            this.excludeFileNames = ImmutableSet.copyOf(excludeFiles);
            this.excludedFileNameSpec = Predicates.or(excludeFileSpecs);
            this.excludedDirNames = ImmutableSet.copyOf(excludeDirs);
        }

        public boolean excludeDir(String name) {
            return excludedDirNames.contains(name);
        }

        public boolean excludeFile(String name) {
            return excludeFileNames.contains(name) || excludedFileNameSpec.apply(name);
        }

        private static class EndMatcher implements Predicate<String> {
            private final String end;

            public EndMatcher(String end) {
                this.end = end;
            }

            @Override
            public boolean apply(String element) {
                return element.endsWith(end);
            }
        }

        private static class StartMatcher implements Predicate<String> {
            private final String start;

            public StartMatcher(String start) {
                this.start = start;
            }

            @Override
            public boolean apply(String element) {
                return element.startsWith(start);
            }
        }
    }

    private static class PathBackedFileTreeElement implements FileTreeElement {
        private final Path path;
        private final String name;
        private final boolean isDirectory;
        private final BasicFileAttributes attrs;
        private final Iterable<String> relativePath;
        private final Stat stat;

        public PathBackedFileTreeElement(Path path, String name, boolean isDirectory, @Nullable BasicFileAttributes attrs, Iterable<String> relativePath, Stat stat) {
            this.path = path;
            this.name = name;
            this.isDirectory = isDirectory;
            this.attrs = attrs;
            this.relativePath = relativePath;
            this.stat = stat;
        }

        @Override
        public File getFile() {
            return path.toFile();
        }

        @Override
        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public long getLastModified() {
            return getAttributes().lastModifiedTime().toMillis();
        }

        @Override
        public long getSize() {
            return getAttributes().size();
        }

        private BasicFileAttributes getAttributes() {
            return Preconditions.checkNotNull(attrs, "Cannot read file attributes of %s", path);
        }

        @Override
        public InputStream open() {
            try {
                return Files.newInputStream(path);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public void copyTo(OutputStream output) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public boolean copyTo(File target) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPath() {
            return getRelativePath().getPathString();
        }

        @Override
        public RelativePath getRelativePath() {
            String[] segments = new String[Iterables.size(relativePath) + 1];
            int i = 0;
            for (String segment : relativePath) {
                segments[i] = segment;
                i++;
            }
            segments[i] = name;
            return new RelativePath(!isDirectory, segments);
        }

        @Override
        public int getMode() {
            return stat.getUnixMode(path.toFile());
        }
    }
}
