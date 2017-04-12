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

import com.google.common.collect.Lists;
import org.gradle.api.internal.cache.StringInterner;

import java.util.List;

public abstract class AbstractResourceSnapshotter implements ResourceSnapshotter, NormalizedSnapshotCollector {
    private final SnapshotNormalizationStrategy normalizationStrategy;
    private final TaskFilePropertyCompareStrategy compareStrategy;
    private final StringInterner stringInterner;
    private final List<NormalizedFileSnapshot> normalizedFileSnapshots;

    public AbstractResourceSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy, StringInterner stringInterner) {
        this.normalizationStrategy = normalizationStrategy;
        this.compareStrategy = compareStrategy;
        this.stringInterner = stringInterner;
        this.normalizedFileSnapshots = Lists.newLinkedList();
    }

    protected void recordSnapshot(FileSnapshot snapshot) {
        NormalizedFileSnapshot normalizedSnapshot = normalizationStrategy.getNormalizedSnapshot(snapshot, stringInterner);
        if (normalizedSnapshot != null) {
            normalizedFileSnapshots.add(normalizedSnapshot);
        }
    }

    @Override
    public void collectSnapshot(NormalizedFileSnapshot snapshot) {
        normalizedFileSnapshots.add(snapshot);
    }

    @Override
    public void finish(NormalizedSnapshotCollector collector) {
        compareStrategy.sort(normalizedFileSnapshots);
        for (NormalizedFileSnapshot normalizedFileSnapshot : normalizedFileSnapshots) {
            collector.collectSnapshot(normalizedFileSnapshot);
        }
    }
}
