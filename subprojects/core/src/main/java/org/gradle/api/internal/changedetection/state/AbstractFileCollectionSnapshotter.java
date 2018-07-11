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

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.logical.DefaultFileCollectionFingerprint;
import org.gradle.api.internal.changedetection.state.mirror.logical.FingerprintingStrategy;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.serialize.Serializers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for calculating a {@link FileCollectionSnapshot} for a particular {@link FileCollection}.
 */
@NonNullApi
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
        registry.register(DefaultFileCollectionFingerprint.class, new DefaultFileCollectionFingerprint.SerializerImpl(stringInterner));
        registry.register(EmptyFileCollectionSnapshot.class, Serializers.constant(EmptyFileCollectionSnapshot.INSTANCE));
    }

    public FileCollectionSnapshot snapshot(FileCollection input, FingerprintingStrategy strategy) {
        FileCollectionInternal fileCollection = (FileCollectionInternal) input;
        FileCollectionVisitorImpl visitor = new FileCollectionVisitorImpl();
        fileCollection.visitRootElements(visitor);
        List<PhysicalSnapshot> roots = visitor.getRoots();
        if (roots.isEmpty()) {
            return EmptyFileCollectionSnapshot.INSTANCE;
        }
        return DefaultFileCollectionFingerprint.from(roots, strategy);
    }

    protected StringInterner getStringInterner() {
        return stringInterner;
    }

    private class FileCollectionVisitorImpl implements FileCollectionVisitor {
        private final List<PhysicalSnapshot> roots = new ArrayList<PhysicalSnapshot>();

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                PhysicalSnapshot fileSnapshot = fileSystemSnapshotter.snapshotSelf(file);
                switch (fileSnapshot.getType()) {
                    case Missing:
                        roots.add(fileSnapshot);
                        break;
                    case RegularFile:
                        roots.add(fileSnapshot);
                        break;
                    case Directory:
                        // Collect the directory and its contents
                        visitDirectoryTree(directoryFileTreeFactory.create(file));
                        break;
                    default:
                        throw new AssertionError();
                }
            }
        }

        @Override
        public void visitTree(FileTreeInternal fileTree) {
            PhysicalSnapshot treeSnapshot = fileSystemSnapshotter.snapshotTree(fileTree);
            roots.add(treeSnapshot);
        }

        @Override
        public void visitDirectoryTree(DirectoryFileTree directoryTree) {
            PhysicalSnapshot treeSnapshot = fileSystemSnapshotter.snapshotDirectoryTree(directoryTree);
            roots.add(treeSnapshot);
        }

        public List<PhysicalSnapshot> getRoots() {
            return roots;
        }
    }
}
