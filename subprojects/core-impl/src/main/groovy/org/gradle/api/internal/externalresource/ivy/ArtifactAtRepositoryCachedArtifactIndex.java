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

package org.gradle.api.internal.externalresource.ivy;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactIdentifier;
import org.gradle.api.internal.externalresource.cached.CachedArtifact;
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex;
import org.gradle.api.internal.externalresource.cached.DefaultCachedArtifact;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.util.BuildCommencedTimeProvider;

import java.io.File;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    public void storeMissing(ArtifactAtRepositoryKey key, BigInteger descriptorHash) {
        storeInternal(key, createMissingEntry(descriptorHash));
    }

    CachedArtifact createMissingEntry(BigInteger descriptorHash) {
        return new DefaultCachedArtifact(timeProvider.getCurrentTime(), descriptorHash);
    }

    private static class ArtifactAtRepositoryKeySerializer implements Serializer<ArtifactAtRepositoryKey> {
        private final ModuleVersionIdentifierSerializer modIdSerializer = new ModuleVersionIdentifierSerializer();

        public void write(Encoder encoder, ArtifactAtRepositoryKey value) throws Exception {
            encoder.writeString(value.getRepositoryId());
            DefaultModuleVersionArtifactIdentifier artifact = (DefaultModuleVersionArtifactIdentifier) value.getArtifactId();
            modIdSerializer.write(encoder, artifact.getModuleVersionIdentifier());
            encoder.writeString(artifact.getName());
            encoder.writeString(artifact.getType());
            encoder.writeNullableString(artifact.getExtension());
            Map<String, String> attributes = artifact.getAttributes();
            encoder.writeSmallInt(attributes.size());
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                encoder.writeString(entry.getKey());
                encoder.writeString(entry.getValue());
            }
        }

        public ArtifactAtRepositoryKey read(Decoder decoder) throws Exception {
            String repositoryId = decoder.readString();
            ModuleVersionIdentifier moduleVersionIdentifier = modIdSerializer.read(decoder);
            String artifactName = decoder.readString();
            String type = decoder.readString();
            String extension = decoder.readNullableString();
            int attrCount = decoder.readSmallInt();
            Map<String, String> attributes;
            if (attrCount == 0) {
                attributes = Collections.emptyMap();
            } else {
                attributes = new HashMap<String, String>(attrCount);
                for (int i = 0; i < attrCount; i++) {
                    String key = decoder.readString();
                    String value = decoder.readString();
                    attributes.put(key, value);
                }
            }
            return new ArtifactAtRepositoryKey(repositoryId, new DefaultModuleVersionArtifactIdentifier(moduleVersionIdentifier, artifactName, type, extension, attributes));
        }
    }

    private static class CachedArtifactSerializer implements Serializer<CachedArtifact> {
        public void write(Encoder encoder, CachedArtifact value) throws Exception {
            encoder.writeBoolean(value.isMissing());
            if (!value.isMissing()) {
                encoder.writeString(value.getCachedFile().getPath());
            }
            encoder.writeLong(value.getCachedAt());
            byte[] hash = value.getDescriptorHash().toByteArray();
            encoder.writeBinary(hash);
        }

        public CachedArtifact read(Decoder decoder) throws Exception {
            boolean isMissing = decoder.readBoolean();
            File file;
            if (!isMissing) {
                file = new File(decoder.readString());
            } else {
                file = null;
            }
            long createTimestamp = decoder.readLong();
            byte[] encodedHash = decoder.readBinary();
            BigInteger hash = new BigInteger(encodedHash);
            return new DefaultCachedArtifact(file, createTimestamp, hash);
        }
    }
}
