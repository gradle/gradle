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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifierSerializer;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.messaging.serialize.SetSerializer;
import org.gradle.util.BuildCommencedTimeProvider;

import java.math.BigInteger;
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

    public CachedArtifacts cacheArtifacts(ModuleVersionRepository repository, ModuleVersionIdentifier moduleMetaDataId, String context, BigInteger descriptorHash, Set<ModuleVersionArtifactIdentifier> artifacts) {
        ModuleArtifactsKey key = new ModuleArtifactsKey(repository.getId(), moduleMetaDataId, context);
        ModuleArtifactsCacheEntry entry = new ModuleArtifactsCacheEntry(artifacts, timeProvider.getCurrentTime(), descriptorHash);
        getCache().put(key, entry);
        return createCacheArtifacts(entry);
    }

    public CachedArtifacts getCachedArtifacts(ModuleVersionRepository repository, ModuleVersionIdentifier moduleMetaDataId, String context) {
        ModuleArtifactsKey key = new ModuleArtifactsKey(repository.getId(), moduleMetaDataId, context);
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
        private final ModuleVersionIdentifier moduleId;
        private final String context;

        private ModuleArtifactsKey(String repositoryId, ModuleVersionIdentifier moduleId, String context) {
            this.repositoryId = repositoryId;
            this.moduleId = moduleId;
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
            return repositoryId.equals(that.repositoryId) && moduleId.equals(that.moduleId) && context.equals(that.context);
        }

        @Override
        public int hashCode() {
            int result = repositoryId.hashCode();
            result = 31 * result + moduleId.hashCode();
            result = 31 * result + context.hashCode();
            return result;
        }
    }

    private static class ModuleArtifactsKeySerializer implements Serializer<ModuleArtifactsKey> {
        private final ModuleVersionIdentifierSerializer identifierSerializer = new ModuleVersionIdentifierSerializer();

        public void write(Encoder encoder, ModuleArtifactsKey value) throws Exception {
            encoder.writeString(value.repositoryId);
            identifierSerializer.write(encoder, value.moduleId);
            encoder.writeString(value.context);
        }

        public ModuleArtifactsKey read(Decoder decoder) throws Exception {
            String resolverId = decoder.readString();
            ModuleVersionIdentifier moduleVersionIdentifier = identifierSerializer.read(decoder);
            String context = decoder.readString();
            return new ModuleArtifactsKey(resolverId, moduleVersionIdentifier, context);
        }
    }

    private static class ModuleArtifactsCacheEntry {
        private final Set<ModuleVersionArtifactIdentifier> artifacts;
        public BigInteger moduleDescriptorHash;
        public long createTimestamp;

        ModuleArtifactsCacheEntry(Set<ModuleVersionArtifactIdentifier> artifacts, long createTimestamp, BigInteger moduleDescriptorHash) {
            this.artifacts = artifacts;
            this.createTimestamp = createTimestamp;
            this.moduleDescriptorHash = moduleDescriptorHash;
        }
    }


    private static class ModuleArtifactsCacheEntrySerializer implements Serializer<ModuleArtifactsCacheEntry> {
        private final Serializer<Set<ModuleVersionArtifactIdentifier>> artifactsSerializer = 
                new SetSerializer<ModuleVersionArtifactIdentifier>(new ModuleVersionArtifactIdentifierSerializer());
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
            Set<ModuleVersionArtifactIdentifier> artifacts = artifactsSerializer.read(decoder);
            return new ModuleArtifactsCacheEntry(artifacts, createTimestamp, hash);
        }
    }

    private static class DefaultCachedArtifacts implements ModuleArtifactsCache.CachedArtifacts {
        private final Set<ModuleVersionArtifactIdentifier> artifacts;
        private final BigInteger descriptorHash;
        private final long ageMillis;

        private DefaultCachedArtifacts(Set<ModuleVersionArtifactIdentifier> artifacts, BigInteger descriptorHash, long ageMillis) {
            this.ageMillis = ageMillis;
            this.artifacts = artifacts;
            this.descriptorHash = descriptorHash;
        }

        public Set<ModuleVersionArtifactIdentifier> getArtifacts() {
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
