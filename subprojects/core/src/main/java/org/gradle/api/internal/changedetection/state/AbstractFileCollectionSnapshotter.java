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
import org.gradle.internal.serialize.SerializerRegistry;

import java.util.List;

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

        ResourceSnapshotter snapshotter = createSnapshotter(snapshotNormalizationStrategy, compareStrategy);
        for (FileSnapshotTree fileTreeSnapshot : fileTreeElements) {
            snapshotter.snapshot(fileTreeSnapshot);
        }
        FileCollectionSnapshotCollector collector = createCollector(snapshotNormalizationStrategy, compareStrategy);
        snapshotter.finish(collector);
        return collector.finish();
    }

    protected abstract FileCollectionSnapshotCollector createCollector(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy);

    protected abstract ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy);
}
