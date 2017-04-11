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

import com.google.common.collect.Maps;
import org.gradle.api.internal.cache.StringInterner;

import java.util.Map;

public class SnapshotCollector {
    private final SnapshotNormalizationStrategy normalizationStrategy;
    private final TaskFilePropertyCompareStrategy compareStrategy;
    private final StringInterner stringInterner;
    Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();

    public SnapshotCollector(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy, StringInterner stringInterner) {
        this.normalizationStrategy = normalizationStrategy;
        this.compareStrategy = compareStrategy;
        this.stringInterner = stringInterner;
    }

    public void recordSnapshot(FileSnapshot fileSnapshot) {
        String absolutePath = fileSnapshot.getPath();
        if (!snapshots.containsKey(absolutePath)) {
            NormalizedFileSnapshot normalizedSnapshot = normalizationStrategy.getNormalizedSnapshot(fileSnapshot, stringInterner);
            if (normalizedSnapshot != null) {
                snapshots.put(absolutePath, normalizedSnapshot);
            }
        }
    }

    public FileCollectionSnapshot finish() {
        return new DefaultFileCollectionSnapshot(snapshots, compareStrategy, normalizationStrategy.isPathAbsolute());
    }
}
