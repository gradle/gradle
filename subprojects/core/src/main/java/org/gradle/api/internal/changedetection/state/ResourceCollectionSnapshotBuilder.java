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

public class ResourceCollectionSnapshotBuilder implements ResourceCollectionVisitor {
    private final Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();
    private final SnapshotNormalizationStrategy snapshotNormalizationStrategy;
    private final StringInterner stringInterner;
    private final TaskFilePropertyCompareStrategy compareStrategy;

    public ResourceCollectionSnapshotBuilder(TaskFilePropertyCompareStrategy compareStrategy, SnapshotNormalizationStrategy snapshotNormalizationStrategy, StringInterner stringInterner) {
        this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
        this.stringInterner = stringInterner;
        this.compareStrategy = compareStrategy;
    }

    @Override
    public void visitResourceTree(FileTreeSnapshot resourceTree) {
        for (FileSnapshot fileSnapshot : resourceTree.getDescendants()) {
            collectFileSnapshot(fileSnapshot);
        }
    }

    @Override
    public void visitDirectory(DirectoryFileSnapshot directory) {
        collectFileSnapshot(directory);
    }

    @Override
    public void visitFile(RegularFileSnapshot file) {
        collectFileSnapshot(file);
    }

    @Override
    public void visitMissingFile(MissingFileSnapshot missingFile) {
        collectFileSnapshot(missingFile);
    }

    public FileCollectionSnapshot build() {
        if (snapshots.isEmpty()) {
            return FileCollectionSnapshot.EMPTY;
        }
        return new DefaultFileCollectionSnapshot(snapshots, compareStrategy, snapshotNormalizationStrategy.isPathAbsolute());
    }

    private void collectFileSnapshot(FileSnapshot fileSnapshot) {
        String absolutePath = fileSnapshot.getPath();
        if (!snapshots.containsKey(absolutePath)) {
            NormalizedFileSnapshot normalizedSnapshot = snapshotNormalizationStrategy.getNormalizedSnapshot(fileSnapshot, stringInterner);
            if (normalizedSnapshot != null) {
                snapshots.put(absolutePath, normalizedSnapshot);
            }
        }
    }
}
