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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.RootFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;

/**
 * Serializes a file collection fingerprint as references to its root fingerprints, which are kept in a {@link RootFingerprintForest}.
 */
public class FileCollectionFingerprintSerializer implements Serializer<FileCollectionFingerprint> {
    private final Interner<String> stringInterner;
    private final RootFingerprintForest rootFingerprints;
    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

    public FileCollectionFingerprintSerializer(Interner<String> stringInterner, RootFingerprintForest rootFingerprints) {
        this.stringInterner = stringInterner;
        this.rootFingerprints = rootFingerprints;
    }

    @Override
    public FileCollectionFingerprint read(Decoder decoder) throws IOException {
        int rootCount = decoder.readSmallInt();
        if (rootCount == 0) {
            return FileCollectionFingerprint.EMPTY;
        }
        HashCode strategyConfigurationHash = hashCodeSerializer.read(decoder);
        HashCode hash = hashCodeSerializer.read(decoder);
        String[] rootPaths = new String[rootCount];
        HashCode[] rootHashes = new HashCode[rootCount];
        for (int i = 0; i < rootCount; i++) {
            rootPaths[i] = stringInterner.intern(decoder.readString());
            rootHashes[i] = hashCodeSerializer.read(decoder);
        }
        // The roots are loaded from the forest later: reading from another cache while this one is being read would deadlock
        return new SerializableFileCollectionFingerprint(() -> loadRoots(rootPaths, rootHashes, strategyConfigurationHash), strategyConfigurationHash, hash);
    }

    private ImmutableList<RootFingerprint> loadRoots(String[] rootPaths, HashCode[] rootHashes, HashCode strategyConfigurationHash) {
        ImmutableList.Builder<RootFingerprint> roots = ImmutableList.builderWithExpectedSize(rootPaths.length);
        for (int i = 0; i < rootPaths.length; i++) {
            roots.add(rootFingerprints.load(rootPaths[i], rootHashes[i], strategyConfigurationHash));
        }
        return roots.build();
    }

    @Override
    public void write(Encoder encoder, FileCollectionFingerprint value) throws Exception {
        ImmutableList<RootFingerprint> roots = value.getRootFingerprints();
        encoder.writeSmallInt(roots.size());
        if (roots.isEmpty()) {
            return;
        }
        hashCodeSerializer.write(encoder, ((SerializableFileCollectionFingerprint) value).getStrategyConfigurationHash());
        hashCodeSerializer.write(encoder, value.getHash());
        for (RootFingerprint root : roots) {
            encoder.writeString(root.getRootPath());
            hashCodeSerializer.write(encoder, root.getRootHash());
        }
    }
}
