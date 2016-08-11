/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultModuleMetaDataCache implements ModuleMetaDataCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModuleMetaDataCache.class);

    private final BuildCommencedTimeProvider timeProvider;
    private final CacheLockingManager cacheLockingManager;

    private final ModuleMetadataStore moduleMetadataStore;
    private PersistentIndexedCache<RevisionKey, ModuleMetadataCacheEntry> cache;

    public DefaultModuleMetaDataCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;

        moduleMetadataStore = new ModuleMetadataStore(new PathKeyFileStore(cacheLockingManager.createMetaDataStore()), new ModuleMetadataSerializer());
    }

    private PersistentIndexedCache<RevisionKey, ModuleMetadataCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<RevisionKey, ModuleMetadataCacheEntry> initCache() {
        return cacheLockingManager.createCache("module-metadata", new RevisionKeySerializer(), new ModuleMetadataCacheEntrySerializer());
    }

    public CachedMetaData getCachedModuleDescriptor(ModuleComponentRepository repository, ModuleComponentIdentifier componentId) {
        ModuleMetadataCacheEntry entry = getCache().get(createKey(repository, componentId));
        if (entry == null) {
            return null;
        }
        if (entry.isMissing()) {
            return new DefaultCachedMetaData(entry, null, timeProvider);
        }
        MutableModuleComponentResolveMetadata metadata = moduleMetadataStore.getModuleDescriptor(repository, componentId);
        if (metadata == null) {
            // Descriptor file has been deleted - ignore the entry
            return null;
        }
        return new DefaultCachedMetaData(entry, entry.configure(metadata), timeProvider);
    }

    public CachedMetaData cacheMissing(ModuleComponentRepository repository, ModuleComponentIdentifier id) {
        LOGGER.debug("Recording absence of module descriptor in cache: {} [changing = {}]", id, false);
        ModuleMetadataCacheEntry entry = ModuleMetadataCacheEntry.forMissingModule(timeProvider.getCurrentTime());
        getCache().put(createKey(repository, id), entry);
        return new DefaultCachedMetaData(entry, null, timeProvider);
    }

    public CachedMetaData cacheMetaData(ModuleComponentRepository repository, ModuleComponentResolveMetadata metadata) {
        LOGGER.debug("Recording module descriptor in cache: {} [changing = {}]", metadata.getComponentId(), metadata.isChanging());
        LocallyAvailableResource resource = moduleMetadataStore.putModuleDescriptor(repository, metadata);
        ModuleMetadataCacheEntry entry = createEntry(metadata, resource.getSha1());
        getCache().put(createKey(repository, metadata.getComponentId()), entry);
        return new DefaultCachedMetaData(entry, null, timeProvider);
    }

    private RevisionKey createKey(ModuleComponentRepository repository, ModuleComponentIdentifier id) {
        return new RevisionKey(repository.getId(), id);
    }

    private ModuleMetadataCacheEntry createEntry(ModuleComponentResolveMetadata metaData, HashValue moduleDescriptorHash) {
        return ModuleMetadataCacheEntry.forMetaData(metaData, timeProvider.getCurrentTime(), moduleDescriptorHash.asBigInteger());
    }

    private static class RevisionKey {
        private final String repositoryId;
        private final ModuleComponentIdentifier componentId;

        private RevisionKey(String repositoryId, ModuleComponentIdentifier componentId) {
            this.repositoryId = repositoryId;
            this.componentId = componentId;
        }

        @Override
        public String toString() {
            return repositoryId + "," + componentId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof RevisionKey)) {
                return false;
            }
            RevisionKey other = (RevisionKey) o;
            return repositoryId.equals(other.repositoryId) && componentId.equals(other.componentId);
        }

        @Override
        public int hashCode() {
            return repositoryId.hashCode() ^ componentId.hashCode();
        }
    }

    private static class RevisionKeySerializer implements Serializer<RevisionKey> {
        private final ComponentIdentifierSerializer componentIdSerializer = new ComponentIdentifierSerializer();

        public void write(Encoder encoder, RevisionKey value) throws Exception {
            encoder.writeString(value.repositoryId);
            componentIdSerializer.write(encoder, value.componentId);
        }

        public RevisionKey read(Decoder decoder) throws Exception {
            String resolverId = decoder.readString();
            ModuleComponentIdentifier identifier = (ModuleComponentIdentifier) componentIdSerializer.read(decoder);
            return new RevisionKey(resolverId, identifier);
        }
    }
}
