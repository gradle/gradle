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

import com.google.common.collect.Lists;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class FileCollectionSnapshotImpl implements FileCollectionSnapshot {
    final Map<String, IncrementalFileSnapshot> snapshots;
    final TaskFilePropertyCompareType compareType;

    public FileCollectionSnapshotImpl(Map<String, IncrementalFileSnapshot> snapshots, TaskFilePropertyCompareType compareType) {
        this.snapshots = snapshots;
        this.compareType = compareType;
    }

    public List<File> getFiles() {
        List<File> files = Lists.newArrayList();
        for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
            if (!(entry.getValue() instanceof DirSnapshot)) {
                files.add(new File(entry.getKey()));
            }
        }
        return files;
    }

    @Override
    public Map<String, IncrementalFileSnapshot> getSnapshots() {
        return snapshots;
    }

    @Override
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, String fileType) {
        return compareType.iterateContentChangesSince(snapshots, oldSnapshot.getSnapshots(), fileType);
    }

    @Override
    public void appendToCacheKey(TaskCacheKeyBuilder builder) {
        compareType.appendToCacheKey(builder, snapshots);
    }
}
