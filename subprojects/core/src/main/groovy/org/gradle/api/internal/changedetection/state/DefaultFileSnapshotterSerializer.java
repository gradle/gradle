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

import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;

import java.util.HashMap;
import java.util.Map;

class DefaultFileSnapshotterSerializer implements Serializer<DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl> {
    public DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl read(Decoder decoder) throws Exception {
        Map<String, DefaultFileCollectionSnapshotter.FileSnapshot> snapshots = new HashMap<String, DefaultFileCollectionSnapshotter.FileSnapshot>();
        DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl snapshot = new DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl(snapshots);
        int snapshotsCount = decoder.readSmallInt();
        for (int i = 0; i < snapshotsCount; i++) {
            String key = decoder.readString();
            byte fileSnapshotKind = decoder.readByte();
            if (fileSnapshotKind == 1) {
                snapshots.put(key, new DefaultFileCollectionSnapshotter.DirSnapshot());
            } else if (fileSnapshotKind == 2) {
                snapshots.put(key, new DefaultFileCollectionSnapshotter.MissingFileSnapshot());
            } else if (fileSnapshotKind == 3) {
                byte hashSize = decoder.readByte();
                byte[] hash = new byte[hashSize];
                decoder.readBytes(hash);
                snapshots.put(key, new DefaultFileCollectionSnapshotter.FileHashSnapshot(hash));
            } else {
                throw new RuntimeException("Unable to read serialized file collection snapshot. Unrecognized value found in the data stream.");
            }
        }
        return snapshot;
    }

    public void write(Encoder encoder, DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl value) throws Exception {
        encoder.writeSmallInt(value.snapshots.size());
        for (String key : value.snapshots.keySet()) {
            encoder.writeString(key);
            DefaultFileCollectionSnapshotter.FileSnapshot fileSnapshot = value.snapshots.get(key);
            if (fileSnapshot instanceof DefaultFileCollectionSnapshotter.DirSnapshot) {
                encoder.writeByte((byte) 1);
            } else if (fileSnapshot instanceof DefaultFileCollectionSnapshotter.MissingFileSnapshot) {
                encoder.writeByte((byte) 2);
            } else if (fileSnapshot instanceof DefaultFileCollectionSnapshotter.FileHashSnapshot) {
                encoder.writeByte((byte) 3);
                byte[] hash = ((DefaultFileCollectionSnapshotter.FileHashSnapshot) fileSnapshot).hash;
                encoder.writeByte((byte) hash.length);
                encoder.writeBytes(hash);
            }
        }
    }
}
