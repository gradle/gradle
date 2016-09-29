/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.cached.ivy;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifierSerializer;
import org.gradle.internal.resource.cached.CachedArtifact;
import org.gradle.internal.resource.cached.CachedArtifactIndex;
import org.gradle.internal.resource.cached.DefaultCachedArtifact;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.BuildCommencedTimeProvider;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class ArtifactAtRepositoryCachedArtifactIndex extends AbstractCachedIndex<ArtifactAtRepositoryKey, CachedArtifact> implements CachedArtifactIndex {
    private final BuildCommencedTimeProvider timeProvider;

    public ArtifactAtRepositoryCachedArtifactIndex(String persistentCacheFile, BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        super(persistentCacheFile, new ArtifactAtRepositoryKeySerializer(), new CachedArtifactSerializer(), cacheLockingManager);
        this.timeProvider = timeProvider;
    }

    private DefaultCachedArtifact createEntry(File artifactFile, BigInteger moduleDescriptorHash) {
        return new DefaultCachedArtifact(artifactFile, timeProvider.getCurrentTime(), moduleDescriptorHash);
    }

    public void store(final ArtifactAtRepositoryKey key, final File artifactFile, BigInteger moduleDescriptorHash) {
        assertArtifactFileNotNull(artifactFile);
        assertKeyNotNull(key);
        storeInternal(key, createEntry(artifactFile, moduleDescriptorHash));
    }

    public void storeMissing(ArtifactAtRepositoryKey key, List<String> attemptedLocations, BigInteger descriptorHash) {
        storeInternal(key, createMissingEntry(attemptedLocations, descriptorHash));
    }

    private CachedArtifact createMissingEntry(List<String> attemptedLocations, BigInteger descriptorHash) {
        return new DefaultCachedArtifact(attemptedLocations, timeProvider.getCurrentTime(), descriptorHash);
    }

    private static class ArtifactAtRepositoryKeySerializer implements Serializer<ArtifactAtRepositoryKey> {
        private final Serializer<ComponentArtifactIdentifier> artifactIdSerializer = new ComponentArtifactIdentifierSerializer();

        public void write(Encoder encoder, ArtifactAtRepositoryKey value) throws Exception {
            encoder.writeString(value.getRepositoryId());
            artifactIdSerializer.write(encoder, value.getArtifactId());
        }

        public ArtifactAtRepositoryKey read(Decoder decoder) throws Exception {
            String repositoryId = decoder.readString();
            ComponentArtifactIdentifier artifactIdentifier = artifactIdSerializer.read(decoder);
            return new ArtifactAtRepositoryKey(repositoryId, artifactIdentifier);
        }
    }

    private static class CachedArtifactSerializer implements Serializer<CachedArtifact> {
        public void write(Encoder encoder, CachedArtifact value) throws Exception {
            encoder.writeBoolean(value.isMissing());
            encoder.writeLong(value.getCachedAt());
            byte[] hash = value.getDescriptorHash().toByteArray();
            encoder.writeBinary(hash);
            if (!value.isMissing()) {
                encoder.writeString(value.getCachedFile().getPath());
            } else {
                encoder.writeSmallInt(value.attemptedLocations().size());
                for (String location : value.attemptedLocations()) {
                    encoder.writeString(location);
                }
            }
        }

        public CachedArtifact read(Decoder decoder) throws Exception {
            boolean isMissing = decoder.readBoolean();
            long createTimestamp = decoder.readLong();
            byte[] encodedHash = decoder.readBinary();
            BigInteger hash = new BigInteger(encodedHash);
            if (!isMissing) {
                File file = new File(decoder.readString());
                return new DefaultCachedArtifact(file, createTimestamp, hash);
            } else {
                int size = decoder.readSmallInt();
                List<String> attempted = new ArrayList<String>(size);
                for (int i = 0; i < size; i++) {
                    attempted.add(decoder.readString());
                }
                return new DefaultCachedArtifact(attempted, createTimestamp, hash);
            }
        }
    }
}
