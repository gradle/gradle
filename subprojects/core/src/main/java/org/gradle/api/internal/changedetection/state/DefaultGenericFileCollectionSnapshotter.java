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
        public GenericResourceSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy, StringInterner stringInterner) {
            super(normalizationStrategy, compareStrategy, stringInterner);
        }

        @Override
        public void snapshot(FileSnapshotTree resource) {
            for (FileSnapshot element : resource.getElements()) {
                recordSnapshot(element);
            }
        }
    }
}
