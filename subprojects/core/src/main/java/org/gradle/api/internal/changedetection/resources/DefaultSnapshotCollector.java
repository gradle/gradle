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

package org.gradle.api.internal.changedetection.resources;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshotCollector;
import org.gradle.api.internal.changedetection.state.SnapshotNormalizationStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;

import java.util.List;

public class DefaultSnapshotCollector implements SnapshotCollector {
    private final SnapshotNormalizationStrategy normalizationStrategy;
    private final TaskFilePropertyCompareStrategy compareStrategy;
    private final StringInterner stringInterner;
    private final List<NormalizedSnapshot> normalizedSnapshots = Lists.newLinkedList();

    public DefaultSnapshotCollector(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy, StringInterner stringInterner) {
        this.normalizationStrategy = normalizationStrategy;
        this.compareStrategy = compareStrategy;
        this.stringInterner = stringInterner;
    }

    @Override
    public void recordSnapshot(SnapshottableResource resource, HashCode hash) {
        NormalizedPath normalizedPath = normalizationStrategy.getNormalizedPath(resource, stringInterner);
        normalizedSnapshots.add(new DefaultNormalizedSnapshot(resource, normalizedPath, hash));
    }

    @Override
    public SnapshotCollector recordSubCollector(SnapshottableResource resource, SnapshotCollector collector) {
        NormalizedPath normalizedPath = normalizationStrategy.getNormalizedPath(resource, stringInterner);
        normalizedSnapshots.add(new SnapshotterCollectorSnapshot(resource, normalizedPath, collector));
        return collector;
    }

    @Override
    public HashCode getHash(NormalizedFileSnapshotCollector collector) {
        compareStrategy.sort(normalizedSnapshots);
        BuildCacheHasher hasher = new DefaultBuildCacheHasher();
        for (NormalizedSnapshot normalizedFileSnapshot : normalizedSnapshots) {
            hasher.putString(normalizedFileSnapshot.getNormalizedPath().getPath());
            hasher.putBytes(normalizedFileSnapshot.getHash(collector).asBytes());
        }
        normalizedSnapshots.clear();
        return hasher.hash();
    }
}
