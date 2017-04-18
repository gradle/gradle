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
import org.gradle.api.internal.changedetection.resources.AbstractSnapshotter;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.IOException;

public class DefaultGenericFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter implements GenericFileCollectionSnapshotter {
    private final StringInterner stringInterner;

    public DefaultGenericFileCollectionSnapshotter(FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner) {
        super(fileSystemSnapshotter, stringInterner);
        this.stringInterner = stringInterner;
    }

    @Override
    protected FileCollectionSnapshotBuilder createFileCollectionSnapshotBuilder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new FileCollectionSnapshotBuilder(normalizationStrategy, compareStrategy, stringInterner);
    }

    @Override
    protected ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new GenericResourceSnapshotter(normalizationStrategy == TaskFilePropertySnapshotNormalizationStrategy.NONE);
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return GenericFileCollectionSnapshotter.class;
    }

    public static class GenericResourceSnapshotter extends AbstractSnapshotter {
        private final boolean noneNormalizationStrategy;

        public GenericResourceSnapshotter(boolean isNoneNormalisationStrategy) {
            noneNormalizationStrategy = isNoneNormalisationStrategy;
        }

        @Override
        protected void snapshotTree(SnapshottableResourceTree tree, SnapshottingResultRecorder recorder) throws IOException {
            for (SnapshottableResource descendant : tree.getDescendants()) {
                snapshotResource(descendant, recorder);
            }
        }

        protected void snapshotResource(SnapshottableResource snapshottable, SnapshottingResultRecorder recorder) {
            if (!noneNormalizationStrategy || snapshottable.getType() != FileType.Directory || !snapshottable.isRoot()) {
                recorder.recordResult(snapshottable, snapshottable.getContent().getContentMd5());
            }
        }
    }
}
