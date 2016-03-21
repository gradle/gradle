/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashValueSerializer;
import org.gradle.internal.serialize.Serializer;

import java.util.HashMap;
import java.util.Map;

class DefaultFileSnapshotterSerializer implements Serializer<FileCollectionSnapshotImpl> {
    private final HashValueSerializer hashValueSerializer = new HashValueSerializer();
    private final StringInterner stringInterner;

    public DefaultFileSnapshotterSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    public FileCollectionSnapshotImpl read(Decoder decoder) throws Exception {
        Map<String, IncrementalFileSnapshot> snapshots = new HashMap<String, IncrementalFileSnapshot>();
        FileCollectionSnapshotImpl snapshot = new FileCollectionSnapshotImpl(snapshots);
        int snapshotsCount = decoder.readSmallInt();
        for (int i = 0; i < snapshotsCount; i++) {
            String key = stringInterner.intern(decoder.readString());
            byte fileSnapshotKind = decoder.readByte();
            if (fileSnapshotKind == 1) {
                snapshots.put(key, DirSnapshot.getInstance());
            } else if (fileSnapshotKind == 2) {
                snapshots.put(key, MissingFileSnapshot.getInstance());
            } else if (fileSnapshotKind == 3) {
                snapshots.put(key, new FileHashSnapshot(hashValueSerializer.read(decoder)));
            } else {
                throw new RuntimeException("Unable to read serialized file collection snapshot. Unrecognized value found in the data stream.");
            }
        }
        return snapshot;
    }

    public void write(Encoder encoder, FileCollectionSnapshotImpl value) throws Exception {
        encoder.writeSmallInt(value.snapshots.size());
        for (String key : value.snapshots.keySet()) {
            encoder.writeString(key);
            IncrementalFileSnapshot incrementalFileSnapshot = value.snapshots.get(key);
            if (incrementalFileSnapshot instanceof DirSnapshot) {
                encoder.writeByte((byte) 1);
            } else if (incrementalFileSnapshot instanceof MissingFileSnapshot) {
                encoder.writeByte((byte) 2);
            } else if (incrementalFileSnapshot instanceof FileHashSnapshot) {
                encoder.writeByte((byte) 3);
                hashValueSerializer.write(encoder, ((FileHashSnapshot) incrementalFileSnapshot).hash);
            }
        }
    }
}
