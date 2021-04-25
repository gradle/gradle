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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import com.google.common.base.Objects;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetadataSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.util.Set;

public class DefaultModuleArtifactsCache extends AbstractArtifactsCache {
    private final ArtifactCacheLockingManager artifactCacheLockingManager;

    private PersistentIndexedCache<ArtifactsAtRepositoryKey, AbstractArtifactsCache.ModuleArtifactsCacheEntry> cache;

    public DefaultModuleArtifactsCache(BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingManager artifactCacheLockingManager) {
        super(timeProvider);
        this.artifactCacheLockingManager = artifactCacheLockingManager;
    }

    private PersistentIndexedCache<ArtifactsAtRepositoryKey, AbstractArtifactsCache.ModuleArtifactsCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<ArtifactsAtRepositoryKey, AbstractArtifactsCache.ModuleArtifactsCacheEntry> initCache() {
        return artifactCacheLockingManager.createCache("module-artifacts", new ModuleArtifactsKeySerializer(), new ModuleArtifactsCacheEntrySerializer());
    }

    @Override
    protected void store(ArtifactsAtRepositoryKey key, AbstractArtifactsCache.ModuleArtifactsCacheEntry entry) {
        getCache().put(key, entry);
    }

    @Override
    protected ModuleArtifactsCacheEntry get(ArtifactsAtRepositoryKey key) {
        return getCache().getIfPresent(key);
    }

    private static class ModuleArtifactsKeySerializer extends AbstractSerializer<ArtifactsAtRepositoryKey> {
        private final ComponentIdentifierSerializer identifierSerializer = new ComponentIdentifierSerializer();

        @Override
        public void write(Encoder encoder, ArtifactsAtRepositoryKey value) throws Exception {
            encoder.writeString(value.repositoryId);
            identifierSerializer.write(encoder, value.componentId);
            encoder.writeString(value.context);
        }

        @Override
        public ArtifactsAtRepositoryKey read(Decoder decoder) throws Exception {
            String resolverId = decoder.readString();
            ComponentIdentifier componentId = identifierSerializer.read(decoder);
            String context = decoder.readString();
            return new ArtifactsAtRepositoryKey(resolverId, componentId, context);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            ModuleArtifactsKeySerializer rhs = (ModuleArtifactsKeySerializer) obj;
            return Objects.equal(identifierSerializer, rhs.identifierSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), identifierSerializer);
        }
    }

    private static class ModuleArtifactsCacheEntrySerializer extends AbstractSerializer<ModuleArtifactsCacheEntry> {
        private final Serializer<Set<ComponentArtifactMetadata>> artifactsSerializer =
            new SetSerializer<>(new ComponentArtifactMetadataSerializer());
        @Override
        public void write(Encoder encoder, ModuleArtifactsCacheEntry value) throws Exception {
            encoder.writeLong(value.createTimestamp);
            byte[] hash = value.moduleDescriptorHash.toByteArray();
            encoder.writeBinary(hash);
            artifactsSerializer.write(encoder, value.artifacts);
        }

        @Override
        public ModuleArtifactsCacheEntry read(Decoder decoder) throws Exception {
            long createTimestamp = decoder.readLong();
            byte[] encodedHash = decoder.readBinary();
            HashCode hash = HashCode.fromBytes(encodedHash);
            Set<ComponentArtifactMetadata> artifacts = artifactsSerializer.read(decoder);
            return new ModuleArtifactsCacheEntry(artifacts, createTimestamp, hash);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            ModuleArtifactsCacheEntrySerializer rhs = (ModuleArtifactsCacheEntrySerializer) obj;
            return Objects.equal(artifactsSerializer, rhs.artifactsSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), artifactsSerializer);
        }
    }
}
