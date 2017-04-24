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

import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.AbstractSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshottableReadableResource;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.changedetection.resources.recorders.DefaultSnapshottingResultRecorder;
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder;
import org.gradle.api.snapshotting.ClasspathEntry;
import org.gradle.api.specs.Spec;

public class ClasspathEntrySnapshotter extends AbstractSnapshotter {
    private final Spec<String> filterSpec;
    private final StringInterner stringInterner;

    public ClasspathEntrySnapshotter(ClasspathEntry entrySnapshotter, StringInterner stringInterner) {
        this.filterSpec = entrySnapshotter.getSpec();
        this.stringInterner = stringInterner;
    }

    @Override
    protected void snapshotResource(SnapshottableResource resource, SnapshottingResultRecorder recorder) {
        if (resource instanceof SnapshottableReadableResource) {
            if (filterSpec.isSatisfiedBy(resource.getPath())) {
                HashCode signatureForClass = resource.getContent().getContentMd5();
                recorder.recordResult(resource, signatureForClass);
            }
        }
    }

    @Override
    protected void snapshotTree(SnapshottableResourceTree snapshottable, SnapshottingResultRecorder recorder) {
        throw new UnsupportedOperationException("Trees cannot be classpath entries");
    }

    @Override
    public SnapshottingResultRecorder createResultRecorder() {
        return new DefaultSnapshottingResultRecorder(TaskFilePropertySnapshotNormalizationStrategy.RELATIVE, TaskFilePropertyCompareStrategy.UNORDERED, stringInterner);
    }
}
