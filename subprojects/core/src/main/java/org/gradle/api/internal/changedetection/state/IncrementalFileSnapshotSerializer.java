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
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import java.io.EOFException;

public class IncrementalFileSnapshotSerializer implements Serializer<IncrementalFileSnapshot> {
    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

    @Override
    public IncrementalFileSnapshot read(Decoder decoder) throws EOFException, Exception {
        byte fileSnapshotKind = decoder.readByte();
        if (fileSnapshotKind == 1) {
            return DirSnapshot.getInstance();
        } else if (fileSnapshotKind == 2) {
            return MissingFileSnapshot.getInstance();
        } else if (fileSnapshotKind == 3) {
            return new FileHashSnapshot(hashCodeSerializer.read(decoder));
        } else {
            throw new RuntimeException("Unable to read serialized file snapshot. Unrecognized value found in the data stream.");
        }
    }

    @Override
    public void write(Encoder encoder, IncrementalFileSnapshot value) throws Exception {
        if (value instanceof DirSnapshot) {
            encoder.writeByte((byte) 1);
        } else if (value instanceof MissingFileSnapshot) {
            encoder.writeByte((byte) 2);
        } else if (value instanceof FileHashSnapshot) {
            encoder.writeByte((byte) 3);
            hashCodeSerializer.write(encoder, value.getHash());
        } else {
            throw new AssertionError();
        }
    }
}
