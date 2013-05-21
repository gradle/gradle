package org.gradle.api.internal.changedetection.state;

import org.gradle.messaging.serialize.DataStreamBackedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
* By Szczepan Faber on 5/21/13
*/
class FileSnapshotSerializer extends DataStreamBackedSerializer<FileCollectionSnapshot> {

    @Override
    public FileCollectionSnapshot read(DataInput dataInput) throws Exception {
        int kind = dataInput.readInt();
        if (kind == 1) {
            DefaultFileSnapshotterSerializer serializer = new DefaultFileSnapshotterSerializer();
            return serializer.read(dataInput);
        } else if (kind == 2) {
            OutputFilesSnapshotter.Serializer serializer = new OutputFilesSnapshotter.Serializer();
            return serializer.read(dataInput);
        } else {
            throw new RuntimeException("Unable to rad from file snapshot cache. Unexpected value read.");
        }
    }

    @Override
    public void write(DataOutput dataOutput, FileCollectionSnapshot value) throws IOException {
        if (value instanceof DefaultFileSnapshotter.FileCollectionSnapshotImpl) {
            dataOutput.writeInt(1);
            DefaultFileSnapshotter.FileCollectionSnapshotImpl cached = (DefaultFileSnapshotter.FileCollectionSnapshotImpl) value;
            DefaultFileSnapshotterSerializer serializer = new DefaultFileSnapshotterSerializer();
            serializer.write(dataOutput, cached);
        } else if (value instanceof OutputFilesSnapshotter.OutputFilesSnapshot) {
            dataOutput.writeInt(2);
            OutputFilesSnapshotter.OutputFilesSnapshot cached = (OutputFilesSnapshotter.OutputFilesSnapshot) value;
            OutputFilesSnapshotter.Serializer serializer = new OutputFilesSnapshotter.Serializer();
            serializer.write(dataOutput, cached);
        } else {
            throw new RuntimeException("Unable to write to file snapshot cache. Unexpected type to write: " + value);
        }
    }
}
