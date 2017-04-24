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
import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.Snapshottable;
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder;

import java.util.List;
import java.util.Map;

public class FileCollectionSnapshotBuilder implements NormalizedFileSnapshotCollector {
    private final SnapshottingResultRecorder resultRecorder;
    private final ResourceSnapshotter snapshotter;
    Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();

    public FileCollectionSnapshotBuilder(ResourceSnapshotter snapshotter) {
        this(snapshotter.createResultRecorder(), snapshotter);
    }

    public FileCollectionSnapshotBuilder(SnapshottingResultRecorder resultRecorder, ResourceSnapshotter snapshotter) {
        this.resultRecorder = resultRecorder;
        this.snapshotter = snapshotter;
    }

    @Override
    public void collectSnapshot(String absolutePath, NormalizedFileSnapshot normalizedSnapshot) {
        if (!snapshots.containsKey(absolutePath)) {
            snapshots.put(absolutePath, normalizedSnapshot);
        }
    }

    public FileCollectionSnapshot build() {
        HashCode hash = resultRecorder.getHash(this);
        return new DefaultFileCollectionSnapshot(snapshots, resultRecorder.getCompareStrategy(), resultRecorder.isNormalizedPathAbsolute(), hash);
    }

    public FileCollectionSnapshotBuilder add(Snapshottable snapshottable) {
        snapshotter.snapshot(snapshottable, resultRecorder);
        return this;
    }

    public FileCollectionSnapshotBuilder addAll(List<Snapshottable> snapshottables) {
        for (Snapshottable snapshottable : snapshottables) {
            add(snapshottable);
        }
        return this;
    }
}
