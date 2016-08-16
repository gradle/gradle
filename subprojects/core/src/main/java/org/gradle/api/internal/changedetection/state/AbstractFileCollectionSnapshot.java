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

import com.google.common.collect.Maps;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.EOFException;
import java.util.Iterator;
import java.util.Map;

abstract class AbstractFileCollectionSnapshot implements FileCollectionSnapshot {
    final Map<String, IncrementalFileSnapshot> snapshots;
    final TaskFilePropertyCompareType compareType;

    public AbstractFileCollectionSnapshot(Map<String, IncrementalFileSnapshot> snapshots, TaskFilePropertyCompareType compareType) {
        this.snapshots = snapshots;
        this.compareType = compareType;
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

    public static class SnapshotMapSerializer implements Serializer<Map<String, IncrementalFileSnapshot>> {
        private final IncrementalFileSnapshotSerializer snapshotSerializer = new IncrementalFileSnapshotSerializer();
        private final StringInterner stringInterner;

        public SnapshotMapSerializer(StringInterner stringInterner) {
            this.stringInterner = stringInterner;
        }

        @Override
        public Map<String, IncrementalFileSnapshot> read(Decoder decoder) throws EOFException, Exception {
            Map<String, IncrementalFileSnapshot> snapshots = Maps.newLinkedHashMap();
            int snapshotsCount = decoder.readSmallInt();
            for (int i = 0; i < snapshotsCount; i++) {
                String path = stringInterner.intern(decoder.readString());
                IncrementalFileSnapshot snapshot = snapshotSerializer.read(decoder);
                snapshots.put(path, snapshot);
            }
            return snapshots;
        }

        @Override
        public void write(Encoder encoder, Map<String, IncrementalFileSnapshot> value) throws Exception {
            encoder.writeSmallInt(value.size());
            for (String key : value.keySet()) {
                encoder.writeString(key);
                IncrementalFileSnapshot snapshot = value.get(key);
                snapshotSerializer.write(encoder, snapshot);
            }
        }
    }
}
