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
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.List;
import java.util.Map;

class DefaultFileCollectionSnapshot extends AbstractFileCollectionSnapshot {
    public DefaultFileCollectionSnapshot(Map<String, IncrementalFileSnapshot> snapshots, TaskFilePropertyCompareType compareType) {
        super(snapshots, compareType);
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

    public static class SerializerImpl implements Serializer<DefaultFileCollectionSnapshot> {
        private final AbstractFileCollectionSnapshot.SnapshotMapSerializer snapshotMapSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.snapshotMapSerializer = new AbstractFileCollectionSnapshot.SnapshotMapSerializer(stringInterner);
        }

        public DefaultFileCollectionSnapshot read(Decoder decoder) throws Exception {
            TaskFilePropertyCompareType compareType = TaskFilePropertyCompareType.values()[decoder.readSmallInt()];
            Map<String, IncrementalFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);
            return new DefaultFileCollectionSnapshot(snapshots, compareType);
        }

        public void write(Encoder encoder, DefaultFileCollectionSnapshot value) throws Exception {
            encoder.writeSmallInt(value.compareType.ordinal());
            snapshotMapSerializer.write(encoder, value.snapshots);
        }
    }
}
