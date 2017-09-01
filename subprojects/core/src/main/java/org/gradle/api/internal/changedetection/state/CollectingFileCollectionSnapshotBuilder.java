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

public class CollectingFileCollectionSnapshotBuilder implements FileCollectionSnapshotBuilder {
    private final Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();
    private final PathNormalizationStrategy pathNormalizationStrategy;
    private final StringInterner stringInterner;
    private final TaskFilePropertyCompareStrategy compareStrategy;

    public CollectingFileCollectionSnapshotBuilder(TaskFilePropertyCompareStrategy compareStrategy, PathNormalizationStrategy pathNormalizationStrategy, StringInterner stringInterner) {
        this.pathNormalizationStrategy = pathNormalizationStrategy;
        this.stringInterner = stringInterner;
        this.compareStrategy = compareStrategy;
    }

    public void collectFileSnapshot(FileSnapshot fileSnapshot) {
        String absolutePath = fileSnapshot.getPath();
        if (!snapshots.containsKey(absolutePath)) {
            NormalizedFileSnapshot normalizedSnapshot = pathNormalizationStrategy.getNormalizedSnapshot(fileSnapshot, stringInterner);
            collectNormalizedFileSnapshot(absolutePath, normalizedSnapshot);
        }
    }

    public void collectNormalizedFileSnapshot(String absolutePath, NormalizedFileSnapshot normalizedSnapshot) {
        if (normalizedSnapshot != null && !snapshots.containsKey(absolutePath)) {
            snapshots.put(absolutePath, normalizedSnapshot);
        }
    }

    public FileCollectionSnapshot build() {
        if (snapshots.isEmpty()) {
            return FileCollectionSnapshot.EMPTY;
        }
        return new DefaultFileCollectionSnapshot(snapshots, compareStrategy, pathNormalizationStrategy.isPathAbsolute());
    }
}
