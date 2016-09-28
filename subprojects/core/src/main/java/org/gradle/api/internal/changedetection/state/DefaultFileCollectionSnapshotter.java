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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.SingletonFileTree;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.api.internal.changedetection.state.FileDetails.FileType.*;
import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED;

/**
 * Responsible for calculating a {@link FileCollectionSnapshot} for a particular {@link FileCollection}.
 *
 * <p>Implementation performs some in-memory caching, should be notified of potential changes by calling {@link #beforeTaskOutputsGenerated()}.</p>
 */
public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter, TaskOutputsGenerationListener {
    private static final DefaultFileCollectionSnapshot EMPTY_SNAPSHOT = new DefaultFileCollectionSnapshot(ImmutableMap.<String, NormalizedFileSnapshot>of(), UNORDERED, true);
    private final FileSnapshotter snapshotter;
    private final StringInterner stringInterner;
    private final FileSystem fileSystem;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    // Map from interned absolute path for a file to known details for the file. Currently used only for root files, not those nested in a directory
    private final Map<String, DefaultFileDetails> rootFiles = new ConcurrentHashMap<String, DefaultFileDetails>();

    public DefaultFileCollectionSnapshotter(FileSnapshotter snapshotter, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.snapshotter = snapshotter;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
    }

    @Override
    public void beforeTaskOutputsGenerated() {
        // When the task outputs are generated, throw away all cached state. This is intentionally very simple, to be improved later
        rootFiles.clear();
    }

    @Override
    public FileCollectionSnapshot emptySnapshot() {
        return EMPTY_SNAPSHOT;
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(DefaultFileCollectionSnapshot.class, new DefaultFileCollectionSnapshot.SerializerImpl(stringInterner));
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection input, TaskFilePropertyCompareStrategy compareStrategy, final SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        final List<DefaultFileDetails> fileTreeElements = Lists.newLinkedList();
        final List<DefaultFileDetails> missingFiles = Lists.newArrayList();
        FileCollectionInternal fileCollection = (FileCollectionInternal) input;
        FileCollectionVisitorImpl visitor = new FileCollectionVisitorImpl(fileTreeElements, missingFiles);
        fileCollection.visitRootElements(visitor);

        if (fileTreeElements.isEmpty() && missingFiles.isEmpty()) {
            return emptySnapshot();
        }

        final Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();
        for (DefaultFileDetails fileDetails : fileTreeElements) {
            String absolutePath = fileDetails.path;
            if (!snapshots.containsKey(absolutePath)) {
                IncrementalFileSnapshot snapshot;
                if (fileDetails.type == Directory) {
                    snapshot = DirSnapshot.getInstance();
                } else {
                    snapshot = new FileHashSnapshot(snapshotter.snapshot(fileDetails.details).getHash(), fileDetails.details.getLastModified());
                }
                NormalizedFileSnapshot normalizedSnapshot = snapshotNormalizationStrategy.getNormalizedSnapshot(fileDetails, snapshot, stringInterner);
                if (normalizedSnapshot != null) {
                    snapshots.put(absolutePath, normalizedSnapshot);
                }
            }
        }
        for (DefaultFileDetails missingFileDetails : missingFiles) {
            String absolutePath = missingFileDetails.path;
            if (!snapshots.containsKey(absolutePath)) {
                snapshots.put(absolutePath, snapshotNormalizationStrategy.getNormalizedSnapshot(missingFileDetails, MissingFileSnapshot.getInstance(), stringInterner));
            }
        }
        return new DefaultFileCollectionSnapshot(snapshots, compareStrategy, snapshotNormalizationStrategy.isPathAbsolute());
    }

    @Override
    public FileCollectionSnapshot snapshot(TaskFilePropertySpec propertySpec) {
        return snapshot(propertySpec.getPropertyFiles(), propertySpec.getCompareStrategy(), propertySpec.getSnapshotNormalizationStrategy());
    }

    private class FileCollectionVisitorImpl implements FileCollectionVisitor, FileVisitor {
        private final List<DefaultFileDetails> fileTreeElements;
        private final List<DefaultFileDetails> missingFiles;

        FileCollectionVisitorImpl(List<DefaultFileDetails> fileTreeElements, List<DefaultFileDetails> missingFiles) {
            this.fileTreeElements = fileTreeElements;
            this.missingFiles = missingFiles;
        }

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                DefaultFileDetails details = rootFiles.get(file.getPath());
                if (details == null) {
                    details = calculateDetails(file);
                    rootFiles.put(details.path, details);
                }
                switch (details.type) {
                    case RegularFile:
                        fileTreeElements.add(details);
                        break;
                    case Directory:
                        // Visit the directory itself, then its contents
                        fileTreeElements.add(details);
                        visitDirectoryTree(directoryFileTreeFactory.create(file));
                        break;
                    case Missing:
                        missingFiles.add(details);
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
            }
        }

        private DefaultFileDetails calculateDetails(File file) {
            String path = getPath(file);
            if (!file.exists()) {
                return new DefaultFileDetails(path, Missing, new MissingFileVisitDetails(file));
            } else if (file.isDirectory()) {
                return new DefaultFileDetails(path, Directory, new SingletonFileTree.SingletonFileVisitDetails(file, fileSystem, true));
            } else {
                return new DefaultFileDetails(path, RegularFile, new SingletonFileTree.SingletonFileVisitDetails(file, fileSystem, false));
            }
        }

        private String getPath(File file) {
            return stringInterner.intern(file.getAbsolutePath());
        }

        @Override
        public void visitTree(FileTreeInternal fileTree) {
            fileTree.visitTreeOrBackingFile(this);
        }

        @Override
        public void visitDirectoryTree(DirectoryFileTree directoryTree) {
            directoryTree.visit(this);
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            fileTreeElements.add(new DefaultFileDetails(getPath(dirDetails.getFile()), Directory, dirDetails));
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            fileTreeElements.add(new DefaultFileDetails(getPath(fileDetails.getFile()), RegularFile, fileDetails));
        }
    }
}
