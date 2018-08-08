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

package org.gradle.internal.fingerprint.impl;

import com.google.common.base.Objects;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.NormalizedFileSnapshot;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SnapshotMapSerializer extends AbstractSerializer<Map<String, NormalizedFileSnapshot>> {
    private static final byte DEFAULT_NORMALIZATION = 1;
    private static final byte IGNORED_PATH_NORMALIZATION = 2;

    private static final byte DIR_SNAPSHOT = 1;
    private static final byte MISSING_FILE_SNAPSHOT = 2;
    private static final byte REGULAR_FILE_SNAPSHOT = 3;

    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();
    private final StringInterner stringInterner;

    public SnapshotMapSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> read(Decoder decoder) throws IOException {
        int snapshotsCount = decoder.readSmallInt();
        Map<String, NormalizedFileSnapshot> snapshots = new LinkedHashMap<String, NormalizedFileSnapshot>(snapshotsCount);
        for (int i = 0; i < snapshotsCount; i++) {
            String absolutePath = stringInterner.intern(decoder.readString());
            NormalizedFileSnapshot snapshot = readSnapshot(decoder);
            snapshots.put(absolutePath, snapshot);
        }
        return snapshots;
    }

    private NormalizedFileSnapshot readSnapshot(Decoder decoder) throws IOException {
        FileType fileType = readFileType(decoder);
        HashCode contentHash = readContentHash(fileType, decoder);

        byte normalizedSnapshotKind = decoder.readByte();
        switch (normalizedSnapshotKind) {
            case DEFAULT_NORMALIZATION:
                String normalizedPath = decoder.readString();
                return new DefaultNormalizedFileSnapshot(stringInterner.intern(normalizedPath), fileType, contentHash);
            case IGNORED_PATH_NORMALIZATION:
                return IgnoredPathFingerprint.create(fileType, contentHash);
            default:
                throw new RuntimeException("Unable to read serialized file snapshot. Unrecognized value found in the data stream.");
        }
    }

    private HashCode readContentHash(FileType fileType, Decoder decoder) throws IOException {
        switch (fileType) {
            case Directory:
                return NormalizedFileSnapshot.DIR_SIGNATURE;
            case Missing:
                return NormalizedFileSnapshot.MISSING_FILE_SIGNATURE;
            case RegularFile:
                return hashCodeSerializer.read(decoder);
            default:
                throw new RuntimeException("Unable to read serialized file snapshot. Unrecognized value found in the data stream.");
        }
    }

    private FileType readFileType(Decoder decoder) throws IOException {
        byte fileSnapshotKind = decoder.readByte();
        switch (fileSnapshotKind) {
            case DIR_SNAPSHOT:
                return FileType.Directory;
            case MISSING_FILE_SNAPSHOT:
                return FileType.Missing;
            case REGULAR_FILE_SNAPSHOT:
                return FileType.RegularFile;
            default:
                throw new RuntimeException("Unable to read serialized file snapshot. Unrecognized value found in the data stream.");
        }
    }

    @Override
    public void write(Encoder encoder, Map<String, NormalizedFileSnapshot> value) throws Exception {
        encoder.writeSmallInt(value.size());
        for (String key : value.keySet()) {
            encoder.writeString(key);
            NormalizedFileSnapshot snapshot = value.get(key);
            writeSnapshot(encoder, snapshot);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        SnapshotMapSerializer rhs = (SnapshotMapSerializer) obj;
        return Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), hashCodeSerializer);
    }

    private void writeSnapshot(Encoder encoder, NormalizedFileSnapshot value) throws IOException {
        switch (value.getType()) {
            case Directory:
                encoder.writeByte(DIR_SNAPSHOT);
                break;
            case Missing:
                encoder.writeByte(MISSING_FILE_SNAPSHOT);
                break;
            case RegularFile:
                encoder.writeByte(REGULAR_FILE_SNAPSHOT);
                hashCodeSerializer.write(encoder, value.getNormalizedContentHash());
                break;
            default:
                throw new AssertionError();
        }

        if (value instanceof DefaultNormalizedFileSnapshot) {
            encoder.writeByte(DEFAULT_NORMALIZATION);
            encoder.writeString(value.getNormalizedPath());
        } else if (value instanceof IgnoredPathFingerprint) {
            encoder.writeByte(IGNORED_PATH_NORMALIZATION);
        } else {
            throw new AssertionError();
        }
    }
}
