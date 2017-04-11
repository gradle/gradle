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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.serialize.SerializerRegistry;

import java.util.List;
import java.util.Map;

/**
 * Responsible for calculating a {@link FileCollectionSnapshot} for a particular {@link FileCollection}.
 */
public abstract class AbstractFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileSnapshotTreeFactory fileSnapshotTreeFactory;
    private final StringInterner stringInterner;

    public AbstractFileCollectionSnapshotter(FileSnapshotTreeFactory fileSnapshotTreeFactory, StringInterner stringInterner) {
        this.fileSnapshotTreeFactory = fileSnapshotTreeFactory;
        this.stringInterner = stringInterner;
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(DefaultFileCollectionSnapshot.class, new DefaultFileCollectionSnapshot.SerializerImpl(stringInterner));
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection input, TaskFilePropertyCompareStrategy compareStrategy, final SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        List<FileSnapshotTree> fileTreeElements = fileSnapshotTreeFactory.fileCollection(input);

        if (fileTreeElements.isEmpty()) {
            return FileCollectionSnapshot.EMPTY;
        }

        Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();
        for (FileSnapshotTree fileTreeSnapshot : fileTreeElements) {
            FileSnapshot root = fileTreeSnapshot.getRoot();
            if (root != null) {
                 if (root.getType() == FileType.RegularFile) {
                     root = normaliseFileElement(root);
                 }
                addNormalizedSnapshot(snapshotNormalizationStrategy, snapshots, root);
            }
            Iterable<? extends FileSnapshot> elements = fileTreeSnapshot.getElements();
            List<FileSnapshot> normalisedElements = normaliseTreeElements(Lists.newArrayList(elements));
            for (FileSnapshot element : normalisedElements) {
                addNormalizedSnapshot(snapshotNormalizationStrategy, snapshots, element);
            }

        }
        return new DefaultFileCollectionSnapshot(snapshots, compareStrategy, snapshotNormalizationStrategy.isPathAbsolute());
    }

    private void addNormalizedSnapshot(SnapshotNormalizationStrategy snapshotNormalizationStrategy, Map<String, NormalizedFileSnapshot> snapshots, FileSnapshot fileSnapshot) {
        String absolutePath = fileSnapshot.getPath();
        if (!snapshots.containsKey(absolutePath)) {
            NormalizedFileSnapshot normalizedSnapshot = snapshotNormalizationStrategy.getNormalizedSnapshot(fileSnapshot, stringInterner);
            if (normalizedSnapshot != null) {
                snapshots.put(absolutePath, normalizedSnapshot);
            }
        }
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
}
