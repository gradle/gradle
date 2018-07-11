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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.SnapshotMapSerializer;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

public class DefaultFileCollectionFingerprint implements FileCollectionSnapshot {

    private final Map<String, NormalizedFileSnapshot> snapshots;
    private final FingerprintCompareStrategy strategy;
    private final Iterable<PhysicalSnapshot> roots;
    private HashCode hash;

    public DefaultFileCollectionFingerprint(Map<String, NormalizedFileSnapshot> snapshots, FingerprintCompareStrategy strategy, @Nullable HashCode hash) {
        this(snapshots, strategy, null, hash);
    }

    public DefaultFileCollectionFingerprint(Iterable<PhysicalSnapshot> roots, FingerprintingStrategy strategy) {
        this(strategy.collectSnapshots(roots), strategy.getCompareStrategy(), roots, null);
    }

    private DefaultFileCollectionFingerprint(Map<String, NormalizedFileSnapshot> snapshots, FingerprintCompareStrategy strategy, @Nullable Iterable<PhysicalSnapshot> roots, @Nullable HashCode hash) {
        this.snapshots = snapshots;
        this.strategy = strategy;
        this.roots = roots;
        this.hash = hash;
    }

    @Override
    public boolean visitChangesSince(FileCollectionSnapshot oldSnapshot, String title, boolean includeAdded, TaskStateChangeVisitor visitor) {
        return strategy.visitChangesSince(visitor, getSnapshots(), oldSnapshot.getSnapshots(), title, includeAdded);
    }

    @Override
    public HashCode getHash() {
        if (hash == null) {
            DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
            strategy.appendToHasher(hasher, snapshots);
            hash = hasher.hash();
        }
        return hash;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return snapshots;
    }

    @Override
    public void visitRoots(PhysicalSnapshotVisitor visitor) {
        if (roots == null) {
            throw new UnsupportedOperationException("Roots not available.");
        }
        for (PhysicalSnapshot root : roots) {
            root.accept(visitor);
        }
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putHash(getHash());
    }

    @VisibleForTesting
    FingerprintCompareStrategy getStrategy() {
        return strategy;
    }

    public static class SerializerImpl implements Serializer<DefaultFileCollectionFingerprint> {

        private final HashCodeSerializer hashCodeSerializer;
        private final SnapshotMapSerializer snapshotMapSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.hashCodeSerializer = new HashCodeSerializer();
            this.snapshotMapSerializer = new SnapshotMapSerializer(stringInterner);
        }

        @Override
        public DefaultFileCollectionFingerprint read(Decoder decoder) throws IOException {
            int type = decoder.readSmallInt();
            FingerprintCompareStrategy compareStrategy = FingerprintCompareStrategy.values()[type];
            boolean hasHash = decoder.readBoolean();
            HashCode hash = hasHash ? hashCodeSerializer.read(decoder) : null;
            Map<String, NormalizedFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);
            return new DefaultFileCollectionFingerprint(snapshots, compareStrategy, hash);
        }

        @Override
        public void write(Encoder encoder, DefaultFileCollectionFingerprint value) throws Exception {
            encoder.writeSmallInt(value.strategy.ordinal());
            encoder.writeBoolean(value.hash != null);
            if (value.hash != null) {
                hashCodeSerializer.write(encoder, value.getHash());
            }
            snapshotMapSerializer.write(encoder, value.getSnapshots());
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            DefaultFileCollectionFingerprint.SerializerImpl rhs = (DefaultFileCollectionFingerprint.SerializerImpl) obj;
            return Objects.equal(snapshotMapSerializer, rhs.snapshotMapSerializer)
                && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), snapshotMapSerializer, hashCodeSerializer);
        }
    }

}
