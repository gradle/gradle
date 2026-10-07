/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.RootFingerprint;
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.impl.IgnoredPathFileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Serializes a {@link RootFingerprint}, storing entry paths relative to the root.
 */
public class RootFingerprintSerializer extends AbstractSerializer<RootFingerprint> {
    private static final byte ROOT_PATH = 1;
    private static final byte RELATIVE_PATH = 2;
    private static final byte ABSOLUTE_PATH = 3;

    private static final byte DEFAULT_NORMALIZATION = 1;
    private static final byte IGNORED_PATH_NORMALIZATION = 2;

    private static final byte DIR_FINGERPRINT = 1;
    private static final byte MISSING_FILE_FINGERPRINT = 2;
    private static final byte REGULAR_FILE_FINGERPRINT = 3;

    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();
    private final Interner<String> stringInterner;

    public RootFingerprintSerializer(Interner<String> stringInterner) {
        this.stringInterner = stringInterner;
    }

    @Override
    public RootFingerprint read(Decoder decoder) throws IOException {
        String rootPath = stringInterner.intern(decoder.readString());
        HashCode rootHash = hashCodeSerializer.read(decoder);
        int count = decoder.readSmallInt();
        ImmutableMap.Builder<String, FileSystemLocationFingerprint> fingerprints = ImmutableMap.builderWithExpectedSize(count);
        for (int i = 0; i < count; i++) {
            String absolutePath = readAbsolutePath(decoder, rootPath);
            fingerprints.put(absolutePath, readFingerprint(decoder));
        }
        return new RootFingerprint(rootPath, rootHash, fingerprints.build());
    }

    @Override
    public void write(Encoder encoder, RootFingerprint value) throws IOException {
        encoder.writeString(value.getRootPath());
        hashCodeSerializer.write(encoder, value.getRootHash());
        encoder.writeSmallInt(value.getFingerprints().size());
        for (Map.Entry<String, FileSystemLocationFingerprint> entry : value.getFingerprints().entrySet()) {
            writeAbsolutePath(encoder, value.getRootPath(), entry.getKey());
            writeFingerprint(encoder, entry.getValue());
        }
    }

    private String readAbsolutePath(Decoder decoder, String rootPath) throws IOException {
        byte pathKind = decoder.readByte();
        switch (pathKind) {
            case ROOT_PATH:
                return rootPath;
            case RELATIVE_PATH:
                return stringInterner.intern(rootPath + File.separatorChar + decoder.readString());
            case ABSOLUTE_PATH:
                return stringInterner.intern(decoder.readString());
            default:
                throw new IOException("Unable to read serialized root fingerprint. Unrecognized path kind " + pathKind + ".");
        }
    }

    private static void writeAbsolutePath(Encoder encoder, String rootPath, String absolutePath) throws IOException {
        if (absolutePath.equals(rootPath)) {
            encoder.writeByte(ROOT_PATH);
        } else if (absolutePath.length() > rootPath.length() + 1
            && absolutePath.startsWith(rootPath)
            && absolutePath.charAt(rootPath.length()) == File.separatorChar) {
            encoder.writeByte(RELATIVE_PATH);
            encoder.writeString(absolutePath.substring(rootPath.length() + 1));
        } else {
            encoder.writeByte(ABSOLUTE_PATH);
            encoder.writeString(absolutePath);
        }
    }

    private FileSystemLocationFingerprint readFingerprint(Decoder decoder) throws IOException {
        FileType fileType = readFileType(decoder);
        HashCode contentHash = readContentHash(fileType, decoder);
        byte fingerprintKind = decoder.readByte();
        switch (fingerprintKind) {
            case DEFAULT_NORMALIZATION:
                String normalizedPath = decoder.readString();
                return new DefaultFileSystemLocationFingerprint(stringInterner.intern(normalizedPath), fileType, contentHash);
            case IGNORED_PATH_NORMALIZATION:
                return IgnoredPathFileSystemLocationFingerprint.create(fileType, contentHash);
            default:
                throw new IOException("Unable to read serialized file fingerprint. Unrecognized value found in the data stream.");
        }
    }

    private HashCode readContentHash(FileType fileType, Decoder decoder) throws IOException {
        switch (fileType) {
            case Directory:
                return FileSystemLocationFingerprint.DIR_SIGNATURE;
            case Missing:
                return FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE;
            case RegularFile:
                return hashCodeSerializer.read(decoder);
            default:
                throw new IOException("Unable to read serialized file fingerprint. Unrecognized value found in the data stream.");
        }
    }

    private static FileType readFileType(Decoder decoder) throws IOException {
        byte fileKind = decoder.readByte();
        switch (fileKind) {
            case DIR_FINGERPRINT:
                return FileType.Directory;
            case MISSING_FILE_FINGERPRINT:
                return FileType.Missing;
            case REGULAR_FILE_FINGERPRINT:
                return FileType.RegularFile;
            default:
                throw new IOException("Unable to read serialized file fingerprint. Unrecognized value found in the data stream.");
        }
    }

    private void writeFingerprint(Encoder encoder, FileSystemLocationFingerprint value) throws IOException {
        switch (value.getType()) {
            case Directory:
                encoder.writeByte(DIR_FINGERPRINT);
                break;
            case Missing:
                encoder.writeByte(MISSING_FILE_FINGERPRINT);
                break;
            case RegularFile:
                encoder.writeByte(REGULAR_FILE_FINGERPRINT);
                hashCodeSerializer.write(encoder, value.getNormalizedContentHash());
                break;
            default:
                throw new AssertionError();
        }
        if (value instanceof DefaultFileSystemLocationFingerprint) {
            encoder.writeByte(DEFAULT_NORMALIZATION);
            encoder.writeString(value.getNormalizedPath());
        } else if (value instanceof IgnoredPathFileSystemLocationFingerprint) {
            encoder.writeByte(IGNORED_PATH_NORMALIZATION);
        } else {
            throw new AssertionError();
        }
    }
}
