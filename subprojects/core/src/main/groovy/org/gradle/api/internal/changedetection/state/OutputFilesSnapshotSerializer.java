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

/**
* By Szczepan Faber on 5/21/13
*/
class OutputFilesSnapshotSerializer extends DataStreamBackedSerializer<FileCollectionSnapshot> {

    @Override
    public FileCollectionSnapshot read(DataInput dataInput) throws Exception {
        Map<String, Long> rootFileIds = new HashMap<String, Long>();
        int rootFileIdsCount = dataInput.readInt();
        for (int i = 0; i < rootFileIdsCount; i++) {
            String key = dataInput.readUTF();
            boolean notNull = dataInput.readBoolean();
            Long value = notNull? dataInput.readLong() : null;
            rootFileIds.put(key, value);
        }
        FileSnapshotSerializer serializer = new FileSnapshotSerializer();
        FileCollectionSnapshot snapshot = serializer.read(dataInput);

        return new OutputFilesSnapshotter.OutputFilesSnapshot(rootFileIds, snapshot);
    }

    @Override
    public void write(DataOutput dataOutput, FileCollectionSnapshot currentValue) throws IOException {
        OutputFilesSnapshotter.OutputFilesSnapshot value = (OutputFilesSnapshotter.OutputFilesSnapshot) currentValue;
        int rootFileIds = value.rootFileIds.size();
        dataOutput.writeInt(rootFileIds);
        for (String key : value.rootFileIds.keySet()) {
            Long id = value.rootFileIds.get(key);
            dataOutput.writeUTF(key);
            if (id == null) {
                dataOutput.writeBoolean(false);
            } else {
                dataOutput.writeBoolean(true);
                dataOutput.writeLong(id);
            }
        }

        FileSnapshotSerializer serializer = new FileSnapshotSerializer();
        serializer.write(dataOutput, value.filesSnapshot);
    }
}
