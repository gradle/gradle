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

import org.gradle.messaging.serialize.DataStreamBackedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class DefaultFileSnapshotterSerializer extends DataStreamBackedSerializer<FileCollectionSnapshot> {

    @Override
    public FileCollectionSnapshot read(DataInput dataInput) throws Exception {
        Map<String, DefaultFileSnapshotter.FileSnapshot> snapshots = new HashMap<String, DefaultFileSnapshotter.FileSnapshot>();
        DefaultFileSnapshotter.FileCollectionSnapshotImpl snapshot = new DefaultFileSnapshotter.FileCollectionSnapshotImpl(snapshots);
        int snapshotsCount = dataInput.readInt();
        for (int i = 0; i < snapshotsCount; i++) {
            String key = dataInput.readUTF();
            byte fileSnapshotKind = dataInput.readByte();
            if (fileSnapshotKind == 1) {
                snapshots.put(key, new DefaultFileSnapshotter.DirSnapshot());
            } else if (fileSnapshotKind == 2) {
                snapshots.put(key, new DefaultFileSnapshotter.MissingFileSnapshot());
            } else if (fileSnapshotKind == 3) {
                byte hashSize = dataInput.readByte();
                byte[] hash = new byte[hashSize];
                dataInput.readFully(hash);
                snapshots.put(key, new DefaultFileSnapshotter.FileHashSnapshot(hash));
            } else {
                throw new RuntimeException("Unable to read serialized file collection snapshot. Unrecognized value found in the data stream.");
            }
        }
        return snapshot;
    }

    @Override
    public void write(DataOutput dataOutput, FileCollectionSnapshot value) throws IOException {
        DefaultFileSnapshotter.FileCollectionSnapshotImpl cached = (DefaultFileSnapshotter.FileCollectionSnapshotImpl) value;
        dataOutput.writeInt(cached.snapshots.size());
        for (String key : cached.snapshots.keySet()) {
            dataOutput.writeUTF(key);
            DefaultFileSnapshotter.FileSnapshot fileSnapshot = cached.snapshots.get(key);
            if (fileSnapshot instanceof DefaultFileSnapshotter.DirSnapshot) {
                dataOutput.writeByte(1);
            } else if (fileSnapshot instanceof DefaultFileSnapshotter.MissingFileSnapshot) {
                dataOutput.writeByte(2);
            } else if (fileSnapshot instanceof DefaultFileSnapshotter.FileHashSnapshot) {
                dataOutput.writeByte(3);
                byte[] hash = ((DefaultFileSnapshotter.FileHashSnapshot) fileSnapshot).hash;
                dataOutput.writeByte(hash.length);
                dataOutput.write(hash);
            }
        }
    }
}
