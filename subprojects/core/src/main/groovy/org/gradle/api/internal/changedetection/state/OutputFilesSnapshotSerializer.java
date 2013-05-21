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
