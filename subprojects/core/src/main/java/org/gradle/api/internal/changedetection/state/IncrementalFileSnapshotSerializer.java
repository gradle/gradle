/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashValueSerializer;
import org.gradle.internal.serialize.Serializer;

import java.io.EOFException;

class IncrementalFileSnapshotSerializer implements Serializer<IncrementalFileSnapshot> {
    private final HashValueSerializer hashValueSerializer = new HashValueSerializer();

    @Override
    public IncrementalFileSnapshot read(Decoder decoder) throws EOFException, Exception {
        byte fileSnapshotKind = decoder.readByte();
        IncrementalFileSnapshot incrementalFileSnapshot;
        if (fileSnapshotKind == 1) {
            incrementalFileSnapshot = DirSnapshot.getInstance();
        } else if (fileSnapshotKind == 2) {
            incrementalFileSnapshot = MissingFileSnapshot.getInstance();
        } else if (fileSnapshotKind == 3) {
            incrementalFileSnapshot = new FileHashSnapshot(hashValueSerializer.read(decoder));
        } else {
            throw new RuntimeException("Unable to read serialized file collection snapshot. Unrecognized value found in the data stream.");
        }
        return incrementalFileSnapshot;
    }

    @Override
    public void write(Encoder encoder, IncrementalFileSnapshot incrementalFileSnapshot) throws Exception {
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
