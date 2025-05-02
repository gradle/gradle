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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Interner;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.Map;

public class FileCollectionFingerprintSerializer implements Serializer<FileCollectionFingerprint> {

    private final FingerprintMapSerializer fingerprintMapSerializer;
    private final Interner<String> stringInterner;
    private final HashCodeSerializer hashCodeSerializer;

    public FileCollectionFingerprintSerializer(Interner<String> stringInterner) {
        this.fingerprintMapSerializer = new FingerprintMapSerializer(stringInterner);
        this.stringInterner = stringInterner;
        this.hashCodeSerializer = new HashCodeSerializer();
    }

    @Override
    public FileCollectionFingerprint read(Decoder decoder) throws IOException {
        Map<String, FileSystemLocationFingerprint> fingerprints = fingerprintMapSerializer.read(decoder);
        if (fingerprints.isEmpty()) {
            return FileCollectionFingerprint.EMPTY;
        }
        ImmutableMultimap<String, HashCode> rootHashes = readRootHashes(decoder);
        HashCode strategyConfigurationHash = hashCodeSerializer.read(decoder);
        return new SerializableFileCollectionFingerprint(fingerprints, rootHashes, strategyConfigurationHash);
    }

    private ImmutableMultimap<String, HashCode> readRootHashes(Decoder decoder) throws IOException {
        int numberOfRoots = decoder.readSmallInt();
        if (numberOfRoots == 0) {
            return ImmutableMultimap.of();
        }
        ImmutableMultimap.Builder<String, HashCode> builder = ImmutableMultimap.builder();
        for (int i = 0; i < numberOfRoots; i++) {
            String absolutePath = stringInterner.intern(decoder.readString());
            HashCode rootHash = hashCodeSerializer.read(decoder);
            builder.put(absolutePath, rootHash);
        }
        return builder.build();
    }

    @Override
    public void write(Encoder encoder, FileCollectionFingerprint value) throws Exception {
        fingerprintMapSerializer.write(encoder, value.getFingerprints());
        if (!value.getFingerprints().isEmpty()) {
            writeRootHashes(encoder, value.getRootHashes());
            hashCodeSerializer.write(encoder, ((SerializableFileCollectionFingerprint) value).getStrategyConfigurationHash());
        }
    }

    private void writeRootHashes(Encoder encoder, ImmutableMultimap<String, HashCode> rootHashes) throws IOException {
        encoder.writeSmallInt(rootHashes.size());
        for (Map.Entry<String, HashCode> entry : rootHashes.entries()) {
            encoder.writeString(entry.getKey());
            hashCodeSerializer.write(encoder, entry.getValue());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FileCollectionFingerprintSerializer that = (FileCollectionFingerprintSerializer) o;
        return fingerprintMapSerializer.equals(that.fingerprintMapSerializer) && hashCodeSerializer.equals(that.hashCodeSerializer);
    }

    @Override
    public int hashCode() {
        int result = fingerprintMapSerializer.hashCode();
        result = 31 * result + hashCodeSerializer.hashCode();
        return result;
    }
}
