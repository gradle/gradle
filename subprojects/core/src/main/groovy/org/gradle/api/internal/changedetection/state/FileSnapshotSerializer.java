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

/**
* By Szczepan Faber on 5/21/13
*/
class FileSnapshotSerializer extends DataStreamBackedSerializer<FileCollectionSnapshot> {

    @Override
    public FileCollectionSnapshot read(DataInput dataInput) throws Exception {
        byte kind = dataInput.readByte();
        if (kind == 1) {
            DefaultFileSnapshotterSerializer serializer = new DefaultFileSnapshotterSerializer();
            return serializer.read(dataInput);
        } else if (kind == 2) {
            OutputFilesSnapshotSerializer serializer = new OutputFilesSnapshotSerializer();
            return serializer.read(dataInput);
        } else {
            throw new RuntimeException("Unable to read from file snapshot cache. Unexpected value found in the data stream.");
        }
    }

    @Override
    public void write(DataOutput dataOutput, FileCollectionSnapshot value) throws IOException {
        if (value instanceof DefaultFileSnapshotter.FileCollectionSnapshotImpl) {
            dataOutput.writeByte(1);
            DefaultFileSnapshotter.FileCollectionSnapshotImpl cached = (DefaultFileSnapshotter.FileCollectionSnapshotImpl) value;
            DefaultFileSnapshotterSerializer serializer = new DefaultFileSnapshotterSerializer();
            serializer.write(dataOutput, cached);
        } else if (value instanceof OutputFilesSnapshotter.OutputFilesSnapshot) {
            dataOutput.writeByte(2);
            OutputFilesSnapshotter.OutputFilesSnapshot cached = (OutputFilesSnapshotter.OutputFilesSnapshot) value;
            OutputFilesSnapshotSerializer serializer = new OutputFilesSnapshotSerializer();
            serializer.write(dataOutput, cached);
        } else {
            throw new RuntimeException("Unable to write to file snapshot cache. Unexpected type to write: " + value);
        }
    }
}
