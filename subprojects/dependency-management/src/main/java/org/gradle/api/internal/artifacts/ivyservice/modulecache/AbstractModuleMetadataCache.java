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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.util.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractModuleMetadataCache implements ModuleMetadataCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistentModuleMetadataCache.class);
    protected final BuildCommencedTimeProvider timeProvider;

    AbstractModuleMetadataCache(BuildCommencedTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    @Override
    public CachedMetadata getCachedModuleDescriptor(ModuleComponentRepository repository, ModuleComponentIdentifier id) {
        final ModuleComponentAtRepositoryKey key = createKey(repository, id);
        return get(key);
    }

    @Override
    public CachedMetadata cacheMissing(ModuleComponentRepository repository, ModuleComponentIdentifier id) {
        LOGGER.debug("Recording absence of module descriptor in cache: {} [changing = {}]", id, false);
        ModuleComponentAtRepositoryKey key = createKey(repository, id);
        ModuleMetadataCacheEntry entry = ModuleMetadataCacheEntry.forMissingModule(timeProvider.getCurrentTime());
        DefaultCachedMetadata cachedMetaData = new DefaultCachedMetadata(entry, null, timeProvider);
        store(key, entry, cachedMetaData);
        return cachedMetaData;
    }

    @Override
    public CachedMetadata cacheMetaData(ModuleComponentRepository repository, ModuleComponentIdentifier id, final ModuleComponentResolveMetadata metadata) {
        LOGGER.debug("Recording module descriptor in cache: {} [changing = {}]", metadata.getId(), metadata.isChanging());
        final ModuleComponentAtRepositoryKey key = createKey(repository, id);
        ModuleMetadataCacheEntry entry = createEntry(metadata);
        DefaultCachedMetadata cachedMetaData = new DefaultCachedMetadata(entry, metadata, timeProvider);
        store(key, entry, cachedMetaData);
        return cachedMetaData;
    }

    protected ModuleComponentAtRepositoryKey createKey(ModuleComponentRepository repository, ModuleComponentIdentifier id) {
        return new ModuleComponentAtRepositoryKey(repository.getId(), id);
    }

    private ModuleMetadataCacheEntry createEntry(ModuleComponentResolveMetadata metaData) {
        return ModuleMetadataCacheEntry.forMetaData(metaData, timeProvider.getCurrentTime());
    }

    protected abstract void store(ModuleComponentAtRepositoryKey key, ModuleMetadataCacheEntry entry, CachedMetadata cachedMetaData);

    protected abstract CachedMetadata get(ModuleComponentAtRepositoryKey key);
}
