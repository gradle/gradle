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

class FileSnapshotSerializer implements Serializer<FileCollectionSnapshot> {

    private final DefaultFileSnapshotterSerializer defaultSnapshotSerializer = new DefaultFileSnapshotterSerializer();
    private final OutputFilesSnapshotSerializer outputSnapshotSerializer = new OutputFilesSnapshotSerializer();

    public FileCollectionSnapshot read(Decoder decoder) throws Exception {
        byte kind = decoder.readByte();
        if (kind == 1) {
            return defaultSnapshotSerializer.read(decoder);
        } else if (kind == 2) {
            return outputSnapshotSerializer.read(decoder);
        } else {
            throw new RuntimeException("Unable to read from file snapshot cache. Unexpected value found in the data stream.");
        }
    }

    public void write(Encoder encoder, FileCollectionSnapshot value) throws Exception {
        if (value instanceof DefaultFileSnapshotter.FileCollectionSnapshotImpl) {
            encoder.writeByte((byte) 1);
            DefaultFileSnapshotter.FileCollectionSnapshotImpl cached = (DefaultFileSnapshotter.FileCollectionSnapshotImpl) value;
            defaultSnapshotSerializer.write(encoder, cached);
        } else if (value instanceof OutputFilesSnapshotter.OutputFilesSnapshot) {
            encoder.writeByte((byte) 2);
            OutputFilesSnapshotter.OutputFilesSnapshot cached = (OutputFilesSnapshotter.OutputFilesSnapshot) value;
            outputSnapshotSerializer.write(encoder, cached);
        } else {
            throw new RuntimeException("Unable to write to file snapshot cache. Unexpected type to write: " + value);
        }
    }
}
