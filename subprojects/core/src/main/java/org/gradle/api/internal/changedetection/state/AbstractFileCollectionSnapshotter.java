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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.List;

/**
 * Responsible for calculating a {@link FileCollectionSnapshot} for a particular {@link FileCollection}.
 */
public abstract class AbstractFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final StringInterner stringInterner;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileSystemSnapshotter fileSystemSnapshotter;

    public AbstractFileCollectionSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter) {
        this.stringInterner = stringInterner;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(DefaultFileCollectionSnapshot.class, new DefaultFileCollectionSnapshot.SerializerImpl(stringInterner));
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection input, TaskFilePropertyCompareStrategy compareStrategy, final SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        FileCollectionInternal fileCollection = (FileCollectionInternal) input;
        ResourceCollectionSnapshotBuilder resourceCollectionSnapshotBuilder = new ResourceCollectionSnapshotBuilder(compareStrategy, snapshotNormalizationStrategy, stringInterner);
        FileCollectionVisitorImpl visitor = new FileCollectionVisitorImpl(resourceCollectionSnapshotBuilder);
        fileCollection.visitRootElements(visitor);
        return resourceCollectionSnapshotBuilder.build();
    }

    /**
     * Normalises the elements of a directory tree. Does not include the root directory.
     */
    protected List<FileSnapshot> normaliseTreeElements(List<FileSnapshot> treeNonRootElements) {
        return treeNonRootElements;
    }

    /**
     * Normalises a root file. Invoked only for top level elements that are regular files.
     */
    protected FileSnapshot normaliseFileElement(FileSnapshot details) {
        return details;
    }

    private class FileCollectionVisitorImpl implements FileCollectionVisitor {
        private final ResourceCollectionSnapshotBuilder resourceCollectionVisitor;

        FileCollectionVisitorImpl(ResourceCollectionSnapshotBuilder resourceCollectionVisitor) {
            this.resourceCollectionVisitor = resourceCollectionVisitor;
        }

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                FileSnapshot fileSnapshot = fileSystemSnapshotter.snapshotSelf(file);
                switch (fileSnapshot.getType()) {
                    case Missing:
                        resourceCollectionVisitor.visitMissingFile((MissingFileSnapshot) fileSnapshot);
                        break;
                    case RegularFile:
                        resourceCollectionVisitor.visitFile((RegularFileSnapshot) normaliseFileElement(fileSnapshot));
                        break;
                    case Directory:
                        // Visit the directory itself, then its contents
                        resourceCollectionVisitor.visitDirectory((DirectoryFileSnapshot) fileSnapshot);
                        visitDirectoryTree(directoryFileTreeFactory.create(file));
                        break;
                    default:
                        throw new AssertionError();
                }
            }
        }

        @Override
        public void visitTree(FileTreeInternal fileTree) {
            List<FileSnapshot> elements = fileSystemSnapshotter.snapshotTree(fileTree);
            elements = normaliseTreeElements(elements);
            resourceCollectionVisitor.visitResourceTree(new DirectoryTreeDetails(null, elements));
        }

        @Override
        public void visitDirectoryTree(DirectoryFileTree directoryTree) {
            FileTreeSnapshot treeSnapshot = fileSystemSnapshotter.snapshotDirectoryTree(directoryTree);
            List<FileSnapshot> elements = treeSnapshot.getDescendants();
            elements = normaliseTreeElements(elements);
            resourceCollectionVisitor.visitResourceTree(new DirectoryTreeDetails(treeSnapshot.getPath(), elements));
        }
    }
}
