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

package org.gradle.api.internal.changedetection.state.mirror;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.nativeintegration.filesystem.DefaultFileMetadata;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.Stat;

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

@SuppressWarnings("Since15")
public class MirrorUpdatingDirectoryWalker {
    private final FileHasher hasher;
    private final FileSystem fileSystem;
    private final StringInterner stringInterner;

    public MirrorUpdatingDirectoryWalker(FileHasher hasher, FileSystem fileSystem, StringInterner stringInterner) {
        this.hasher = hasher;
        this.fileSystem = fileSystem;
        this.stringInterner = stringInterner;
    }

    public FileSystemSnapshot walk(final PhysicalSnapshot fileSnapshot) {
        return walk(fileSnapshot, null);
    }

    public FileSystemSnapshot walk(final PhysicalSnapshot fileSnapshot, @Nullable PatternSet patterns) {
        if (fileSnapshot.getType() == FileType.Missing) {
            // The root missing file should not be tracked for trees.
            return FileSystemSnapshot.EMPTY;
        }
        if (fileSnapshot.getType() == FileType.RegularFile) {
            return fileSnapshot;
        }
        Path rootPath = Paths.get(fileSnapshot.getAbsolutePath());
        return walkDir(rootPath, patterns);
    }

    private PhysicalSnapshot walkDir(Path rootPath, @Nullable PatternSet patterns) {
        final Spec<FileTreeElement> spec = patterns == null ? null : patterns.getAsSpec();
        final MerkleDirectorySnapshotBuilder builder = new MerkleDirectorySnapshotBuilder();

        try {
            Files.walkFileTree(rootPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new java.nio.file.FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = stringInterner.intern(dir.getFileName().toString());
                    if (builder.isRoot() || isAllowed(dir, name, true, attrs, builder.getRelativePathSegmentsTracker())) {
                        builder.preVisitDirectory(internedAbsolutePath(dir), name);
                        return FileVisitResult.CONTINUE;
                    } else {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                @Override
                public FileVisitResult visitFile(Path file, @Nullable BasicFileAttributes attrs) {
                    String name = stringInterner.intern(file.getFileName().toString());
                    if (isAllowed(file, name, false, attrs, builder.getRelativePathSegmentsTracker())) {
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
                    if (isNotFileSystemLoopException(exc) && isAllowed(file, file.getFileName().toString(), false, null, builder.getRelativePathSegmentsTracker())) {
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
                    PhysicalFileSnapshot fileSnapshot = new PhysicalFileSnapshot(internedAbsolutePath(file), name, hash, metadata.getLastModified());
                    builder.visit(fileSnapshot);
                }

                private String internedAbsolutePath(Path file) {
                    return stringInterner.intern(file.toString());
                }

                private boolean isAllowed(Path path, String name, boolean isDirectory, @Nullable BasicFileAttributes attrs, RelativePathSegmentsTracker relativePath) {
                    if (spec == null) {
                        return true;
                    }
                    relativePath.enter(name);
                    boolean allowed = spec.isSatisfiedBy(new PathBackedFileTreeElement(path, name, isDirectory, attrs, relativePath.getRelativePath(), fileSystem));
                    relativePath.leave();
                    return allowed;
                }
            });
        } catch (IOException e) {
            throw new GradleException(String.format("Could not list contents of directory '%s'.", rootPath), e);
        }
        return builder.getResult();
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
            return path.toString();
        }

        @Override
        public RelativePath getRelativePath() {
            return new RelativePath(!isDirectory, Iterables.toArray(relativePath, String.class));
        }

        @Override
        public int getMode() {
            return stat.getUnixMode(path.toFile());
        }
    }
}
