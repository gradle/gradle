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

package org.gradle.api.internal.changedetection.resources.recorders;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.changedetection.resources.paths.NormalizedPath;
import org.gradle.api.internal.changedetection.resources.results.CompositeSnapshottingResult;
import org.gradle.api.internal.changedetection.resources.results.NormalizedResourceSnapshottingResult;
import org.gradle.api.internal.changedetection.resources.results.SnapshottingResult;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshotCollector;
import org.gradle.api.internal.changedetection.state.SnapshotNormalizationStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;

import java.util.LinkedList;
import java.util.List;

public class DefaultSnapshottingResultRecorder implements SnapshottingResultRecorder {
    private final SnapshotNormalizationStrategy normalizationStrategy;
    private final TaskFilePropertyCompareStrategy compareStrategy;
    private final StringInterner stringInterner;
    private final List<SnapshottingResult> normalizedResources = new LinkedList<SnapshottingResult>();

    public DefaultSnapshottingResultRecorder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy, StringInterner stringInterner) {
        this.normalizationStrategy = normalizationStrategy;
        this.compareStrategy = compareStrategy;
        this.stringInterner = stringInterner;
    }

    @Override
    public void recordResult(SnapshottableResource resource, HashCode hash) {
        NormalizedPath normalizedPath = normalizationStrategy.getNormalizedPath(resource, stringInterner);
        normalizedResources.add(new NormalizedResourceSnapshottingResult(resource, normalizedPath, hash));
    }

    @Override
    public SnapshottingResultRecorder recordCompositeResult(SnapshottableResource resource, SnapshottingResultRecorder recorder) {
        NormalizedPath normalizedPath = normalizationStrategy.getNormalizedPath(resource, stringInterner);
        normalizedResources.add(new CompositeSnapshottingResult(resource, normalizedPath, recorder));
        return recorder;
    }

    @Override
    public HashCode getHash(NormalizedFileSnapshotCollector collector) {
        compareStrategy.sort(normalizedResources);
        BuildCacheHasher hasher = new DefaultBuildCacheHasher();
        for (SnapshottingResult normalizedFileSnapshot : normalizedResources) {
            hasher.putString(normalizedFileSnapshot.getNormalizedPath().getPath());
            hasher.putBytes(normalizedFileSnapshot.getHash(collector).asBytes());
        }
        normalizedResources.clear();
        return hasher.hash();
    }
}
