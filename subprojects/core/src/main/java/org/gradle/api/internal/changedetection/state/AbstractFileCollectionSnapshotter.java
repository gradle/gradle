/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.api.internal.changedetection.state.FileDetails.FileType.*;

/**
 * Responsible for calculating a {@link FileCollectionSnapshot} for a particular {@link FileCollection}.
 */
public abstract class AbstractFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileHasher hasher;
    private final StringInterner stringInterner;
    private final FileSystem fileSystem;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileSystemMirror fileSystemMirror;

    public AbstractFileCollectionSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileSystemMirror = fileSystemMirror;
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(DefaultFileCollectionSnapshot.class, new DefaultFileCollectionSnapshot.SerializerImpl(stringInterner));
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection input, TaskFilePropertyCompareStrategy compareStrategy, final SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        final List<FileDetails> fileTreeElements = Lists.newLinkedList();
        FileCollectionInternal fileCollection = (FileCollectionInternal) input;
        FileCollectionVisitorImpl visitor = new FileCollectionVisitorImpl(fileTreeElements);
        fileCollection.visitRootElements(visitor);

        if (fileTreeElements.isEmpty()) {
            return FileCollectionSnapshot.EMPTY;
        }

        Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();
        for (FileDetails fileDetails : fileTreeElements) {
            String absolutePath = fileDetails.getPath();
            if (!snapshots.containsKey(absolutePath)) {
                NormalizedFileSnapshot normalizedSnapshot = snapshotNormalizationStrategy.getNormalizedSnapshot(fileDetails, stringInterner);
                if (normalizedSnapshot != null) {
                    snapshots.put(absolutePath, normalizedSnapshot);
                }
            }
        }
        return new DefaultFileCollectionSnapshot(snapshots, compareStrategy, snapshotNormalizationStrategy.isPathAbsolute());
    }

    private DirSnapshot dirSnapshot() {
        return DirSnapshot.getInstance();
    }

    private MissingFileSnapshot missingFileSnapshot() {
        return MissingFileSnapshot.getInstance();
    }

    private FileHashSnapshot fileSnapshot(FileTreeElement fileDetails) {
        return new FileHashSnapshot(hasher.hash(fileDetails), fileDetails.getLastModified());
    }

    private String getPath(File file) {
        return stringInterner.intern(file.getAbsolutePath());
    }

    protected List<FileDetails> normalise(List<FileDetails> treeNonRootElements) {
        return treeNonRootElements;
    }

    private class FileCollectionVisitorImpl implements FileCollectionVisitor {
        private final List<FileDetails> fileTreeElements;

        FileCollectionVisitorImpl(List<FileDetails> fileTreeElements) {
            this.fileTreeElements = fileTreeElements;
        }

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                FileDetails details = fileSystemMirror.getFile(file.getPath());
                if (details == null) {
                    details = calculateDetails(file);
                    fileSystemMirror.putFile(details);
                }
                switch (details.getType()) {
                    case Missing:
                    case RegularFile:
                        fileTreeElements.add(details);
                        break;
                    case Directory:
                        // Visit the directory itself, then its contents
                        fileTreeElements.add(details);
                        visitDirectoryTree(directoryFileTreeFactory.create(file));
                        break;
                    default:
                        throw new AssertionError();
                }
            }
        }

        private DefaultFileDetails calculateDetails(File file) {
            String path = getPath(file);
            if (!file.exists()) {
                return new DefaultFileDetails(path, new RelativePath(true, file.getName()), Missing, true, missingFileSnapshot());
            } else if (file.isDirectory()) {
                return new DefaultFileDetails(path, new RelativePath(false, file.getName()), Directory, true, dirSnapshot());
            } else {
                FileVisitDetails fileDetails = new DefaultFileVisitDetails(file, new RelativePath(true, file.getName()), new AtomicBoolean(), fileSystem, fileSystem, false);
                return new DefaultFileDetails(path, new RelativePath(true, file.getName()), RegularFile, true, fileSnapshot(fileDetails));
            }
        }

        @Override
        public void visitTree(FileTreeInternal fileTree) {
            List<FileDetails> elements = Lists.newArrayList();
            fileTree.visitTreeOrBackingFile(new FileVisitorImpl(elements));
            elements = normalise(elements);
            fileTreeElements.addAll(elements);
        }

        @Override
        public void visitDirectoryTree(DirectoryFileTree directoryTree) {
            List<FileDetails> elements;
            if (!directoryTree.getPatterns().isEmpty()) {
                // Currently handle only those trees where we want everything from a directory
                elements = Lists.newArrayList();
                directoryTree.visit(new FileVisitorImpl(elements));
            } else {
                DirectoryTreeDetails treeDetails = fileSystemMirror.getDirectoryTree(directoryTree.getDir().getAbsolutePath());
                if (treeDetails != null) {
                    // Reuse the details
                    elements = treeDetails.elements;
                } else {
                    // Scan the directory
                    String path = getPath(directoryTree.getDir());
                    elements = Lists.newArrayList();
                    directoryTree.visit(new FileVisitorImpl(elements));
                    DirectoryTreeDetails details = new DirectoryTreeDetails(path, ImmutableList.copyOf(elements));
                    fileSystemMirror.putDirectory(details);
                }
            }

            elements = normalise(elements);
            fileTreeElements.addAll(elements);
        }
    }

    private class FileVisitorImpl implements FileVisitor {
        private final List<FileDetails> fileTreeElements;

        FileVisitorImpl(List<FileDetails> fileTreeElements) {
            this.fileTreeElements = fileTreeElements;
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            fileTreeElements.add(new DefaultFileDetails(getPath(dirDetails.getFile()), dirDetails.getRelativePath(), Directory, false, dirSnapshot()));
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            fileTreeElements.add(new DefaultFileDetails(getPath(fileDetails.getFile()), fileDetails.getRelativePath(), RegularFile, false, fileSnapshot(fileDetails)));
        }
    }
}
