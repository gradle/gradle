/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.AbstractResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.IOException;

public class DefaultGenericFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter implements GenericFileCollectionSnapshotter {
    private final StringInterner stringInterner;

    public DefaultGenericFileCollectionSnapshotter(FileSnapshotTreeFactory fileSnapshotTreeFactory, StringInterner stringInterner) {
        super(fileSnapshotTreeFactory, stringInterner);
        this.stringInterner = stringInterner;
    }

    @Override
    protected FileCollectionSnapshotCollector createCollector(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new FileCollectionSnapshotCollector(normalizationStrategy, compareStrategy);
    }

    @Override
    protected ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new GenericResourceSnapshotter(normalizationStrategy, compareStrategy, stringInterner);
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return GenericFileCollectionSnapshotter.class;
    }

    public static class GenericResourceSnapshotter extends AbstractResourceSnapshotter {
        private final boolean noneNormalizationStrategy;

        public GenericResourceSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy, StringInterner stringInterner) {
            super(normalizationStrategy, compareStrategy, stringInterner);
            noneNormalizationStrategy = normalizationStrategy == TaskFilePropertySnapshotNormalizationStrategy.NONE;
        }

        @Override
        public void snapshot(SnapshotTree resource) {
            try {
                for (SnapshottableResource element : resource.getElements()) {
                    if (!noneNormalizationStrategy || element.getType() != FileType.Directory || resource.getRoot() != element) {
                        recordSnapshot(element, element.getContent().getContentMd5());
                    }
                }
            } catch (IOException e) {
                IoActions.closeQuietly(resource);
            }
        }
    }
}
