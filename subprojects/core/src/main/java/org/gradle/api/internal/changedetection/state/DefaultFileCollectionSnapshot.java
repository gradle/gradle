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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class DefaultFileCollectionSnapshot implements FileCollectionSnapshot {
    private final Map<String, NormalizedFileSnapshot> snapshots;
    private final TaskFilePropertyCompareStrategy compareStrategy;

    public DefaultFileCollectionSnapshot(Map<String, NormalizedFileSnapshot> snapshots, TaskFilePropertyCompareStrategy compareStrategy) {
        this.snapshots = snapshots;
        this.compareStrategy = compareStrategy;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return snapshots;
    }

    @Override
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, String fileType) {
        return compareStrategy.iterateContentChangesSince(snapshots, oldSnapshot.getSnapshots(), fileType);
    }

    @Override
    public void appendToCacheKey(TaskCacheKeyBuilder builder) {
        compareStrategy.appendToCacheKey(builder, snapshots);
    }

    @Override
    public List<File> getFiles() {
        List<File> files = Lists.newArrayList();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : snapshots.entrySet()) {
            if (!(entry.getValue().getSnapshot() instanceof DirSnapshot)) {
                files.add(new File(entry.getKey()));
            }
        }
        return files;
    }

    public static class SerializerImpl implements Serializer<DefaultFileCollectionSnapshot> {
        private final SnapshotMapSerializer snapshotMapSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.snapshotMapSerializer = new SnapshotMapSerializer(stringInterner);
        }

        public DefaultFileCollectionSnapshot read(Decoder decoder) throws Exception {
            TaskFilePropertyCompareStrategy compareStrategy = TaskFilePropertyCompareStrategy.values()[decoder.readSmallInt()];
            Map<String, NormalizedFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);
            return new DefaultFileCollectionSnapshot(snapshots, compareStrategy);
        }

        public void write(Encoder encoder, DefaultFileCollectionSnapshot value) throws Exception {
            encoder.writeSmallInt(value.compareStrategy.ordinal());
            snapshotMapSerializer.write(encoder, value.snapshots);
        }
    }
}
