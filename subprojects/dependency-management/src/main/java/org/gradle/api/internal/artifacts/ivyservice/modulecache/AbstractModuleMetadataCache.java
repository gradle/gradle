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

import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.util.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractModuleMetadataCache implements ModuleMetadataCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModuleMetadataCache.class);
    protected final BuildCommencedTimeProvider timeProvider;

    AbstractModuleMetadataCache(BuildCommencedTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public CachedMetadata getCachedModuleDescriptor(ModuleMetadataDetails details) {
        final ModuleComponentAtRepositoryKey key = createKey(details);
        return get(key);
    }

    public CachedMetadata cacheMissing(ModuleMetadataDetails details) {
        LOGGER.debug("Recording absence of module descriptor in cache: {} [changing = {}]", details.getModuleComponentIdentifier(), false);
        ModuleComponentAtRepositoryKey key = createKey(details);
        ModuleMetadataCacheEntry entry = ModuleMetadataCacheEntry.forMissingModule(timeProvider.getCurrentTime());
        DefaultCachedMetadata cachedMetaData = new DefaultCachedMetadata(entry, null, timeProvider);
        store(key, entry, cachedMetaData);
        return cachedMetaData;
    }

    public CachedMetadata cacheMetaData(ModuleMetadataDetails details, final ModuleComponentResolveMetadata metadata) {
        LOGGER.debug("Recording module descriptor in cache: {} [changing = {}]", metadata.getId(), metadata.isChanging());
        final ModuleComponentAtRepositoryKey key = createKey(details);
        ModuleMetadataCacheEntry entry = createEntry(metadata);
        DefaultCachedMetadata cachedMetaData = new DefaultCachedMetadata(entry, metadata, timeProvider);
        store(key, entry, cachedMetaData);
        return cachedMetaData;
    }

    private ModuleComponentAtRepositoryKey createKey(ModuleMetadataDetails details) {
        return new ModuleComponentAtRepositoryKey(details.getRepository().getId(), details.getModuleComponentIdentifier());
    }

    private ModuleMetadataCacheEntry createEntry(ModuleComponentResolveMetadata metaData) {
        return ModuleMetadataCacheEntry.forMetaData(metaData, timeProvider.getCurrentTime());
    }

    protected abstract void store(ModuleComponentAtRepositoryKey key, ModuleMetadataCacheEntry entry, CachedMetadata cachedMetaData);

    protected abstract CachedMetadata get(ModuleComponentAtRepositoryKey key);
}
