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

class DefaultFileSnapshotterSerializer implements Serializer<FileCollectionSnapshot> {
    public FileCollectionSnapshot read(Decoder decoder) throws Exception {
        Map<String, DefaultFileSnapshotter.FileSnapshot> snapshots = new HashMap<String, DefaultFileSnapshotter.FileSnapshot>();
        DefaultFileSnapshotter.FileCollectionSnapshotImpl snapshot = new DefaultFileSnapshotter.FileCollectionSnapshotImpl(snapshots);
        int snapshotsCount = decoder.readInt();
        for (int i = 0; i < snapshotsCount; i++) {
            String key = decoder.readString();
            byte fileSnapshotKind = decoder.readByte();
            if (fileSnapshotKind == 1) {
                snapshots.put(key, new DefaultFileSnapshotter.DirSnapshot());
            } else if (fileSnapshotKind == 2) {
                snapshots.put(key, new DefaultFileSnapshotter.MissingFileSnapshot());
            } else if (fileSnapshotKind == 3) {
                byte hashSize = decoder.readByte();
                byte[] hash = new byte[hashSize];
                decoder.readBytes(hash);
                snapshots.put(key, new DefaultFileSnapshotter.FileHashSnapshot(hash));
            } else {
                throw new RuntimeException("Unable to read serialized file collection snapshot. Unrecognized value found in the data stream.");
            }
        }
        return snapshot;
    }

    public void write(Encoder encoder, FileCollectionSnapshot value) throws Exception {
        DefaultFileSnapshotter.FileCollectionSnapshotImpl cached = (DefaultFileSnapshotter.FileCollectionSnapshotImpl) value;
        encoder.writeInt(cached.snapshots.size());
        for (String key : cached.snapshots.keySet()) {
            encoder.writeString(key);
            DefaultFileSnapshotter.FileSnapshot fileSnapshot = cached.snapshots.get(key);
            if (fileSnapshot instanceof DefaultFileSnapshotter.DirSnapshot) {
                encoder.writeByte((byte) 1);
            } else if (fileSnapshot instanceof DefaultFileSnapshotter.MissingFileSnapshot) {
                encoder.writeByte((byte) 2);
            } else if (fileSnapshot instanceof DefaultFileSnapshotter.FileHashSnapshot) {
                encoder.writeByte((byte) 3);
                byte[] hash = ((DefaultFileSnapshotter.FileHashSnapshot) fileSnapshot).hash;
                encoder.writeByte((byte) hash.length);
                encoder.writeBytes(hash);
            }
        }
    }
}
