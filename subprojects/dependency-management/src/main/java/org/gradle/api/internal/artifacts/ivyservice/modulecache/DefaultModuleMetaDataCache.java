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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyXmlModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.util.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultModuleMetaDataCache implements ModuleMetaDataCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModuleMetaDataCache.class);

    private final BuildCommencedTimeProvider timeProvider;
    private final CacheLockingManager cacheLockingManager;

    private final ModuleDescriptorStore moduleDescriptorStore;
    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> cache;

    public DefaultModuleMetaDataCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager, ResolverStrategy resolverStrategy) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;

        moduleDescriptorStore = new ModuleDescriptorStore(new PathKeyFileStore(cacheLockingManager.createMetaDataStore()), new IvyXmlModuleDescriptorWriter(), new IvyXmlModuleDescriptorParser(resolverStrategy));
    }

    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> initCache() {
        return cacheLockingManager.createCache("module-metadata", new RevisionKeySerializer(), new ModuleDescriptorCacheEntrySerializer());
    }

    public CachedMetaData getCachedModuleDescriptor(ModuleComponentRepository repository, ModuleComponentIdentifier componentId) {
        ModuleDescriptorCacheEntry entry = getCache().get(createKey(repository, componentId));
        if (entry == null) {
            return null;
        }
        if (entry.isMissing()) {
            return new DefaultCachedMetaData(entry, entry.createMetaData(null), timeProvider);
        }
        ModuleDescriptor descriptor = moduleDescriptorStore.getModuleDescriptor(repository, componentId);
        if (descriptor == null) {
            // Descriptor file has been deleted - ignore the entry
            return null;
        }
        return new DefaultCachedMetaData(entry, entry.createMetaData(descriptor), timeProvider);
    }

    public CachedMetaData cacheMissing(ModuleComponentRepository repository, ModuleComponentIdentifier id) {
        LOGGER.debug("Recording absence of module descriptor in cache: {} [changing = {}]", id, false);
        ModuleDescriptorCacheEntry entry = ModuleDescriptorCacheEntry.forMissingModule(timeProvider.getCurrentTime());
        getCache().put(createKey(repository, id), entry);
        return new DefaultCachedMetaData(entry, null, timeProvider);
    }

    public CachedMetaData cacheMetaData(ModuleComponentRepository repository, ModuleComponentResolveMetaData metaData) {
        ModuleDescriptor moduleDescriptor = metaData.getDescriptor();
        LOGGER.debug("Recording module descriptor in cache: {} [changing = {}]", moduleDescriptor.getModuleRevisionId(), metaData.isChanging());
        LocallyAvailableResource resource = moduleDescriptorStore.putModuleDescriptor(repository, moduleDescriptor);
        ModuleDescriptorCacheEntry entry = createEntry(metaData, resource.getSha1());
        getCache().put(createKey(repository, metaData.getComponentId()), entry);
        return new DefaultCachedMetaData(entry, null, timeProvider);
    }

    private RevisionKey createKey(ModuleComponentRepository repository, ModuleComponentIdentifier id) {
        return new RevisionKey(repository.getId(), id);
    }

    private ModuleDescriptorCacheEntry createEntry(ModuleComponentResolveMetaData metaData, HashValue moduleDescriptorHash) {
        return ModuleDescriptorCacheEntry.forMetaData(metaData, timeProvider.getCurrentTime(), moduleDescriptorHash.asBigInteger());
    }

    private static class RevisionKey {
        private final String repositoryId;
        private final ModuleComponentIdentifier componentId;

        private RevisionKey(String repositoryId, ModuleComponentIdentifier componentId) {
            this.repositoryId = repositoryId;
            this.componentId = componentId;
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
