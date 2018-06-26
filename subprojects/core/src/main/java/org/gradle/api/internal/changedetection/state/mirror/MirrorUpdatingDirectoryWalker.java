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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("Since15")
public class MirrorUpdatingDirectoryWalker {
    private final FileHasher hasher;
    private final FileSystem fileSystem;

    public MirrorUpdatingDirectoryWalker(FileHasher hasher, FileSystem fileSystem) {
        this.hasher = hasher;
        this.fileSystem = fileSystem;
    }

    public HierarchicalVisitableTree walkDir(final FileSnapshot fileSnapshot) {
        return walkDir(fileSnapshot, null);
    }

    public HierarchicalVisitableTree walkDir(final FileSnapshot fileSnapshot, @Nullable PatternSet patterns) {
        if (fileSnapshot.getType() == FileType.Missing) {
            return PhysicalSnapshotBackedVisitableTree.EMPTY;
        }
        if (fileSnapshot.getType() == FileType.RegularFile) {
            return new HierarchicalVisitableTree() {

                @Override
                public void accept(HierarchicalFileTreeVisitor visitor) {
                    visitor.visit(Paths.get(fileSnapshot.getPath()), fileSnapshot.getName(), fileSnapshot.getContent());
                }
            };
        }
        Path rootPath = Paths.get(fileSnapshot.getPath());
        ImmutablePhysicalDirectorySnapshot rootDirectory = walkDir(rootPath, patterns);
        return new PhysicalSnapshotBackedVisitableTree(rootDirectory);
    }

    public ImmutablePhysicalDirectorySnapshot walkDir(final Path rootPath) {
        return walkDir(rootPath, null);
    }

    public ImmutablePhysicalDirectorySnapshot walkDir(final Path rootPath, @Nullable PatternSet patterns) {
        final Spec<FileTreeElement> spec = patterns == null ? null : patterns.getAsSpec();
        final Deque<String> relativePathHolder = new ArrayDeque<String>();
        final Deque<ImmutableList.Builder<PhysicalSnapshot>> levelHolder = new ArrayDeque<ImmutableList.Builder<PhysicalSnapshot>>();
        final AtomicReference<ImmutablePhysicalDirectorySnapshot> result = new AtomicReference<ImmutablePhysicalDirectorySnapshot>();
        final AtomicReference<String> rootDirectoryName = new AtomicReference<String>();

        try {
            Files.walkFileTree(rootPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new java.nio.file.FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (levelHolder.size() == 0 || isAllowed(dir, true, attrs, relativePathHolder)) {
                        String name = internedName(dir);
                        if (levelHolder.size() == 0) {
                            rootDirectoryName.set(name);
                        } else {
                            relativePathHolder.addLast(name);

                        }
                        levelHolder.addLast(ImmutableList.<PhysicalSnapshot>builder());
                        return FileVisitResult.CONTINUE;
                    } else {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                @Override
                public FileVisitResult visitFile(Path file, @Nullable BasicFileAttributes attrs) {
                    if (isAllowed(file, false, attrs, relativePathHolder)) {
                        if (attrs != null && attrs.isSymbolicLink()) {
                            // when FileVisitOption.FOLLOW_LINKS, we only get here when link couldn't be followed
                            throw new GradleException(String.format("Could not list contents of '%s'. Couldn't follow symbolic link.", file));
                        }
                        addFileSnapshot(file, attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, @Nullable IOException exc) {
                    if (isNotFileSystemLoopException(exc) && isAllowed(file, false, null, relativePathHolder)) {
                        throw new GradleException(String.format("Could not read path '%s'.", file), exc);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) {
                    if (isNotFileSystemLoopException(exc)) {
                        throw new GradleException(String.format("Could not read directory path '%s'.", dir), exc);
                    }
                    String directoryPath = relativePathHolder.isEmpty() ? rootDirectoryName.get() : relativePathHolder.removeLast();
                    ImmutableList.Builder<PhysicalSnapshot> builder = levelHolder.removeLast();
                    ImmutablePhysicalDirectorySnapshot directorySnapshot = new ImmutablePhysicalDirectorySnapshot(dir, directoryPath, builder.build());
                    ImmutableList.Builder<PhysicalSnapshot> parentBuilder = levelHolder.peekLast();
                    if (parentBuilder != null) {
                        parentBuilder.add(directorySnapshot);
                    } else {
                        result.set(directorySnapshot);
                    }
                    return FileVisitResult.CONTINUE;
                }

                private boolean isNotFileSystemLoopException(@Nullable IOException e) {
                    return e != null && !(e instanceof FileSystemLoopException);
                }

                private void addFileSnapshot(Path file, @Nullable BasicFileAttributes attrs) {
                    Preconditions.checkNotNull(attrs, "Unauthorized access to %", file);
                    String name = internedName(file);
                    DefaultFileMetadata metadata = new DefaultFileMetadata(FileType.RegularFile, attrs.lastModifiedTime().toMillis(), attrs.size());
                    HashCode hash = hasher.hash(file.toFile(), metadata);
                    PhysicalFileSnapshot fileSnapshot = new PhysicalFileSnapshot(file, name, metadata.getLastModified(), hash);
                    levelHolder.peekLast().add(fileSnapshot);
                }

                private String internedName(Path dir) {
                    return RelativePath.PATH_SEGMENT_STRING_INTERNER.intern(dir.getFileName().toString());
                }

                private boolean isAllowed(Path path, boolean isDirectory, @Nullable BasicFileAttributes attrs, Deque<String> relativePath) {
                    if (spec == null) {
                        return true;
                    }
                    relativePath.addLast(path.getFileName().toString());
                    boolean allowed = spec.isSatisfiedBy(new PathBackedFileTreeElement(path, isDirectory, attrs, relativePath, fileSystem));
                    relativePath.removeLast();
                    return allowed;
                }
            });
        } catch (IOException e) {
            throw new GradleException(String.format("Could not list contents of directory '%s'.", rootPath), e);
        }
        return result.get();
    }

    private static class PathBackedFileTreeElement implements FileTreeElement {
        private final Path path;
        private final boolean isDirectory;
        private final BasicFileAttributes attrs;
        private final Iterable<String> relativePath;
        private final Stat stat;

        public PathBackedFileTreeElement(Path path, boolean isDirectory, @Nullable BasicFileAttributes attrs, Iterable<String> relativePath, Stat stat) {
            this.path = path;
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
            return attrs.lastModifiedTime().toMillis();
        }

        @Override
        public long getSize() {
            return attrs.size();
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
            return path.getFileName().toString();
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
