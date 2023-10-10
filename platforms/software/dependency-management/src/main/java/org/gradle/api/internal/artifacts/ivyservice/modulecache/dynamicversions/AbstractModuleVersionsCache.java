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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.util.internal.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public abstract class AbstractModuleVersionsCache implements ModuleVersionsCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModuleVersionsCache.class);
    protected final BuildCommencedTimeProvider timeProvider;

    public AbstractModuleVersionsCache(BuildCommencedTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    @Override
    public void cacheModuleVersionList(ModuleComponentRepository<?> repository, ModuleIdentifier moduleId, Set<String> listedVersions) {
        LOGGER.debug("Caching version list in module versions cache: Using '{}' for '{}'", listedVersions, moduleId);
        ModuleAtRepositoryKey key = createKey(repository, moduleId);
        ModuleVersionsCacheEntry entry = createEntry(listedVersions);
        store(key, entry);
    }

    @Override
    public CachedModuleVersionList getCachedModuleResolution(ModuleComponentRepository<?> repository, ModuleIdentifier moduleId) {
        ModuleAtRepositoryKey key = createKey(repository, moduleId);
        ModuleVersionsCacheEntry entry = get(key);
        return entry == null ? null : versionList(entry);
    }

    private CachedModuleVersionList versionList(ModuleVersionsCacheEntry entry) {
        return new DefaultCachedModuleVersionList(
            entry.moduleVersionListing,
            timeProvider.getCurrentTime() - entry.createTimestamp
        );
    }

    private ModuleAtRepositoryKey createKey(ModuleComponentRepository<?> repository, ModuleIdentifier moduleId) {
        return new ModuleAtRepositoryKey(repository.getId(), moduleId);
    }

    private ModuleVersionsCacheEntry createEntry(Set<String> listedVersions) {
        return new ModuleVersionsCacheEntry(listedVersions, timeProvider.getCurrentTime());
    }

    protected abstract void store(ModuleAtRepositoryKey key, ModuleVersionsCacheEntry entry);

    protected abstract ModuleVersionsCacheEntry get(ModuleAtRepositoryKey key);
}
