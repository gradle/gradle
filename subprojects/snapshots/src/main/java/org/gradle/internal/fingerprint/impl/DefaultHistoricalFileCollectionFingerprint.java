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

package org.gradle.internal.fingerprint.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMultimap;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.changes.TaskStateChangeVisitor;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintCompareStrategy;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class DefaultHistoricalFileCollectionFingerprint implements HistoricalFileCollectionFingerprint {

    private final Map<String, FileSystemLocationFingerprint> fingerprints;
    private final FingerprintCompareStrategy compareStrategy;
    private final ImmutableMultimap<String, HashCode> rootHashes;

    public DefaultHistoricalFileCollectionFingerprint(Map<String, FileSystemLocationFingerprint> fingerprints, FingerprintCompareStrategy compareStrategy, ImmutableMultimap<String, HashCode> rootHashes) {
        this.fingerprints = fingerprints;
        this.compareStrategy = compareStrategy;
        this.rootHashes = rootHashes;
    }

    @Override
    public boolean visitChangesSince(FileCollectionFingerprint oldFingerprint, String title, boolean includeAdded, TaskStateChangeVisitor visitor) {
        return compareStrategy.visitChangesSince(visitor, getFingerprints(), oldFingerprint.getFingerprints(), title, includeAdded);
    }

    @VisibleForTesting
    FingerprintCompareStrategy getCompareStrategy() {
        return compareStrategy;
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> getFingerprints() {
        return fingerprints;
    }

    @Override
    public ImmutableMultimap<String, HashCode> getRootHashes() {
        return rootHashes;
    }

    public static class SerializerImpl implements Serializer<DefaultHistoricalFileCollectionFingerprint> {

        private final FingerprintMapSerializer fingerprintMapSerializer;
        private final StringInterner stringInterner;
        private final HashCodeSerializer hashCodeSerializer;
        private final BiMap<Integer, FingerprintCompareStrategy> compareStrategies;

        public SerializerImpl(StringInterner stringInterner, List<FingerprintCompareStrategy> compareStrategies) {
            this.fingerprintMapSerializer = new FingerprintMapSerializer(stringInterner);
            this.stringInterner = stringInterner;
            this.hashCodeSerializer = new HashCodeSerializer();
            this.compareStrategies = mapStrategies(compareStrategies);
        }

        private static BiMap<Integer, FingerprintCompareStrategy> mapStrategies(List<FingerprintCompareStrategy> compareStrategies) {
            ImmutableBiMap.Builder<Integer, FingerprintCompareStrategy> builder = ImmutableBiMap.builder();
            ListIterator<FingerprintCompareStrategy> iStrategy = compareStrategies.listIterator();
            while (iStrategy.hasNext()) {
                int index = iStrategy.nextIndex();
                FingerprintCompareStrategy strategy = iStrategy.next();
                builder.put(index, strategy);
            }
            return builder.build();
        }

        @Override
        public DefaultHistoricalFileCollectionFingerprint read(Decoder decoder) throws IOException {
            int type = decoder.readSmallInt();
            FingerprintCompareStrategy compareStrategy = compareStrategies.get(type);
            Map<String, FileSystemLocationFingerprint> fingerprints = fingerprintMapSerializer.read(decoder);
            ImmutableMultimap<String, HashCode> rootHashes = readRootHashes(decoder);
            return new DefaultHistoricalFileCollectionFingerprint(fingerprints, compareStrategy, rootHashes);
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
        public void write(Encoder encoder, DefaultHistoricalFileCollectionFingerprint value) throws Exception {
            encoder.writeSmallInt(compareStrategies.inverse().get(value.compareStrategy));
            fingerprintMapSerializer.write(encoder, value.getFingerprints());
            writeRootHashes(encoder, value.getRootHashes());
        }

        private void writeRootHashes(Encoder encoder, ImmutableMultimap<String, HashCode> rootHashes) throws IOException {
            encoder.writeSmallInt(rootHashes.size());
            for (Map.Entry<String, HashCode> entry : rootHashes.entries()) {
                encoder.writeString(entry.getKey());
                hashCodeSerializer.write(encoder, entry.getValue());
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            DefaultHistoricalFileCollectionFingerprint.SerializerImpl rhs = (DefaultHistoricalFileCollectionFingerprint.SerializerImpl) obj;
            return Objects.equal(fingerprintMapSerializer, rhs.fingerprintMapSerializer)
                && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), fingerprintMapSerializer, hashCodeSerializer);
        }
    }
}
