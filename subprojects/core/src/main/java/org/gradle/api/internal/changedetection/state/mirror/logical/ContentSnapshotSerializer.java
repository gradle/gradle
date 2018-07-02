/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
import org.gradle.api.internal.changedetection.state.MissingFileContentSnapshot;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.IOException;

public class ContentSnapshotSerializer extends AbstractSerializer<FileContentSnapshot> {
    private static final byte DIR_SNAPSHOT = 1;
    private static final byte MISSING_FILE_SNAPSHOT = 2;
    private static final byte REGULAR_FILE_SNAPSHOT = 3;

    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

    @Override
    public FileContentSnapshot read(Decoder decoder) throws IOException {
        byte fileSnapshotKind = decoder.readByte();
        switch (fileSnapshotKind) {
            case DIR_SNAPSHOT:
                return DirContentSnapshot.INSTANCE;
            case MISSING_FILE_SNAPSHOT:
                return MissingFileContentSnapshot.INSTANCE;
            case REGULAR_FILE_SNAPSHOT:
                return new FileHashSnapshot(hashCodeSerializer.read(decoder));
            default:
                throw new RuntimeException("Unable to read serialized file snapshot. Unrecognized value found in the data stream.");
        }
    }

    @Override
    public void write(Encoder encoder, FileContentSnapshot value) throws IOException {
        if (value instanceof DirContentSnapshot) {
            encoder.writeByte(DIR_SNAPSHOT);
        } else if (value instanceof MissingFileContentSnapshot) {
            encoder.writeByte(MISSING_FILE_SNAPSHOT);
        } else if (value instanceof FileHashSnapshot) {
            encoder.writeByte(REGULAR_FILE_SNAPSHOT);
            hashCodeSerializer.write(encoder, value.getContentMd5());
        } else {
            throw new AssertionError();
        }

    }
}
