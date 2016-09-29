/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetadataSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.util.BuildCommencedTimeProvider;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultModuleArtifactsCache implements ModuleArtifactsCache {
    private final BuildCommencedTimeProvider timeProvider;
    private final CacheLockingManager cacheLockingManager;
    private PersistentIndexedCache<ModuleArtifactsKey, ModuleArtifactsCacheEntry> cache;

    public DefaultModuleArtifactsCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
    }

    private PersistentIndexedCache<ModuleArtifactsKey, ModuleArtifactsCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<ModuleArtifactsKey, ModuleArtifactsCacheEntry> initCache() {
        return cacheLockingManager.createCache("module-artifacts", new ModuleArtifactsKeySerializer(), new ModuleArtifactsCacheEntrySerializer());
    }

    public CachedArtifacts cacheArtifacts(ModuleComponentRepository repository, ComponentIdentifier componentId, String context, BigInteger descriptorHash, Set<? extends ComponentArtifactMetadata> artifacts) {
        ModuleArtifactsKey key = new ModuleArtifactsKey(repository.getId(), componentId, context);
        ModuleArtifactsCacheEntry entry = new ModuleArtifactsCacheEntry(artifacts, timeProvider.getCurrentTime(), descriptorHash);
        getCache().put(key, entry);
        return createCacheArtifacts(entry);
    }

    public CachedArtifacts getCachedArtifacts(ModuleComponentRepository repository, ComponentIdentifier componentId, String context) {
        ModuleArtifactsKey key = new ModuleArtifactsKey(repository.getId(), componentId, context);
        ModuleArtifactsCacheEntry entry = getCache().get(key);
        if (entry == null) {
            return null;
        }
        return createCacheArtifacts(entry);
    }

    private CachedArtifacts createCacheArtifacts(ModuleArtifactsCacheEntry entry) {
        long entryAge = timeProvider.getCurrentTime() - entry.createTimestamp;
        return new DefaultCachedArtifacts(entry.artifacts, entry.moduleDescriptorHash, entryAge);
    }

    private static class ModuleArtifactsKey {
        private final String repositoryId;
        private final ComponentIdentifier componentId;
        private final String context;

        private ModuleArtifactsKey(String repositoryId, ComponentIdentifier componentId, String context) {
            this.repositoryId = repositoryId;
            this.componentId = componentId;
            this.context = context;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ModuleArtifactsKey)) {
                return false;
            }

            ModuleArtifactsKey that = (ModuleArtifactsKey) o;
            return repositoryId.equals(that.repositoryId) && componentId.equals(that.componentId) && context.equals(that.context);
        }

        @Override
        public int hashCode() {
            int result = repositoryId.hashCode();
            result = 31 * result + componentId.hashCode();
            result = 31 * result + context.hashCode();
            return result;
        }
    }

    private static class ModuleArtifactsKeySerializer implements Serializer<ModuleArtifactsKey> {
        private final ComponentIdentifierSerializer identifierSerializer = new ComponentIdentifierSerializer();

        public void write(Encoder encoder, ModuleArtifactsKey value) throws Exception {
            encoder.writeString(value.repositoryId);
            identifierSerializer.write(encoder, value.componentId);
            encoder.writeString(value.context);
        }

        public ModuleArtifactsKey read(Decoder decoder) throws Exception {
            String resolverId = decoder.readString();
            ComponentIdentifier componentId = identifierSerializer.read(decoder);
            String context = decoder.readString();
            return new ModuleArtifactsKey(resolverId, componentId, context);
        }
    }

    private static class ModuleArtifactsCacheEntry {
        private final Set<ComponentArtifactMetadata> artifacts;
        private final BigInteger moduleDescriptorHash;
        private final long createTimestamp;

        ModuleArtifactsCacheEntry(Set<? extends ComponentArtifactMetadata> artifacts, long createTimestamp, BigInteger moduleDescriptorHash) {
            this.artifacts = new LinkedHashSet<ComponentArtifactMetadata>(artifacts);
            this.createTimestamp = createTimestamp;
            this.moduleDescriptorHash = moduleDescriptorHash;
        }
    }

    private static class ModuleArtifactsCacheEntrySerializer implements Serializer<ModuleArtifactsCacheEntry> {
        private final Serializer<Set<ComponentArtifactMetadata>> artifactsSerializer =
                new SetSerializer<ComponentArtifactMetadata>(new ComponentArtifactMetadataSerializer());
        public void write(Encoder encoder, ModuleArtifactsCacheEntry value) throws Exception {
            encoder.writeLong(value.createTimestamp);
            byte[] hash = value.moduleDescriptorHash.toByteArray();
            encoder.writeBinary(hash);
            artifactsSerializer.write(encoder, value.artifacts);
        }

        public ModuleArtifactsCacheEntry read(Decoder decoder) throws Exception {
            long createTimestamp = decoder.readLong();
            byte[] encodedHash = decoder.readBinary();
            BigInteger hash = new BigInteger(encodedHash);
            Set<ComponentArtifactMetadata> artifacts = artifactsSerializer.read(decoder);
            return new ModuleArtifactsCacheEntry(artifacts, createTimestamp, hash);
        }
    }

    private static class DefaultCachedArtifacts implements ModuleArtifactsCache.CachedArtifacts {
        private final Set<ComponentArtifactMetadata> artifacts;
        private final BigInteger descriptorHash;
        private final long ageMillis;

        private DefaultCachedArtifacts(Set<ComponentArtifactMetadata> artifacts, BigInteger descriptorHash, long ageMillis) {
            this.ageMillis = ageMillis;
            this.artifacts = artifacts;
            this.descriptorHash = descriptorHash;
        }

        public Set<ComponentArtifactMetadata> getArtifacts() {
            return artifacts;
        }

        public BigInteger getDescriptorHash() {
            return descriptorHash;
        }

        public long getAgeMillis() {
            return ageMillis;
        }
    }

}
