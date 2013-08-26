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

class OutputFilesSnapshotSerializer implements Serializer<FileCollectionSnapshot> {

    public FileCollectionSnapshot read(Decoder decoder) throws Exception {
        Map<String, Long> rootFileIds = new HashMap<String, Long>();
        int rootFileIdsCount = decoder.readInt();
        for (int i = 0; i < rootFileIdsCount; i++) {
            String key = decoder.readString();
            boolean notNull = decoder.readBoolean();
            Long value = notNull? decoder.readLong() : null;
            rootFileIds.put(key, value);
        }
        FileSnapshotSerializer serializer = new FileSnapshotSerializer();
        FileCollectionSnapshot snapshot = serializer.read(decoder);

        return new OutputFilesSnapshotter.OutputFilesSnapshot(rootFileIds, snapshot);
    }

    public void write(Encoder encoder, FileCollectionSnapshot currentValue) throws Exception {
        OutputFilesSnapshotter.OutputFilesSnapshot value = (OutputFilesSnapshotter.OutputFilesSnapshot) currentValue;
        int rootFileIds = value.rootFileIds.size();
        encoder.writeInt(rootFileIds);
        for (String key : value.rootFileIds.keySet()) {
            Long id = value.rootFileIds.get(key);
            encoder.writeString(key);
            if (id == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeLong(id);
            }
        }

        FileSnapshotSerializer serializer = new FileSnapshotSerializer();
        serializer.write(encoder, value.filesSnapshot);
    }
}
