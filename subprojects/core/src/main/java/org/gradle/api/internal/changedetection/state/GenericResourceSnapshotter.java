/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.changedetection.resources.recorders.DefaultSnapshottingResultRecorder;
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.IOException;

public class GenericResourceSnapshotter extends AbstractResourceSnapshotter {
    private final boolean noneNormalizationStrategy;
    private final SnapshotNormalizationStrategy normalizationStrategy;
    private final TaskFilePropertyCompareStrategy compareStrategy;
    private final StringInterner stringInterner;

    public GenericResourceSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy, StringInterner stringInterner) {
        noneNormalizationStrategy = normalizationStrategy == TaskFilePropertySnapshotNormalizationStrategy.NONE;
        this.normalizationStrategy = normalizationStrategy;
        this.compareStrategy = compareStrategy;
        this.stringInterner = stringInterner;
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

    @Override
    public SnapshottingResultRecorder createResultRecorder() {
        return new DefaultSnapshottingResultRecorder(normalizationStrategy, compareStrategy, stringInterner);
    }
}
