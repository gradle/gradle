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

import com.google.common.hash.HashCode;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.Snapshottable;
import org.gradle.api.internal.changedetection.snapshotting.SnapshotterCacheKey;
import org.gradle.api.snapshotting.Snapshotter;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.serialize.SerializerRegistry;

import java.util.List;

/**
 * Responsible for calculating a {@link FileCollectionSnapshot} for a particular {@link FileCollection}.
 */
public abstract class AbstractFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final StringInterner stringInterner;

    public AbstractFileCollectionSnapshotter(FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.stringInterner = stringInterner;
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(DefaultFileCollectionSnapshot.class, new DefaultFileCollectionSnapshot.SerializerImpl(stringInterner));
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection input, TaskFilePropertyCompareStrategy compareStrategy, final SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        List<Snapshottable> snapshottables = fileSystemSnapshotter.fileCollection(input);

        if (snapshottables.isEmpty()) {
            return FileCollectionSnapshot.EMPTY;
        }

        FileCollectionSnapshotBuilder builder = createFileCollectionSnapshotBuilder(snapshotNormalizationStrategy, compareStrategy);

        ResourceSnapshotter snapshotter = createSnapshotter(snapshotNormalizationStrategy, compareStrategy);
        for (Snapshottable snapshottable : snapshottables) {
            snapshotter.snapshot(snapshottable, builder);
        }
        return builder.build();
    }

    protected HashCode hashConfiguration(ValueSnapshotter valueSnapshotter, SnapshotterCacheKey snapshotterCacheKey) {
        BuildCacheHasher hasher = new DefaultBuildCacheHasher();
        hasher.putString(snapshotterCacheKey.getSnapshotterClass().getName());
        for (Snapshotter snapshotter : snapshotterCacheKey.getConfigurations()) {
            hasher.putString(snapshotter.getClass().getName());
            valueSnapshotter.snapshot(snapshotter.getInputs()).appendToHasher(hasher);
        }
        return hasher.hash();
    }

    protected abstract FileCollectionSnapshotBuilder createFileCollectionSnapshotBuilder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy);

    protected abstract ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy);
}
