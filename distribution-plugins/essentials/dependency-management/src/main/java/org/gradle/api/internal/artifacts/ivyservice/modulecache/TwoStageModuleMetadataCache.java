/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.util.BuildCommencedTimeProvider;

public class TwoStageModuleMetadataCache extends AbstractModuleMetadataCache {
    private final AbstractModuleMetadataCache readOnlyCache;
    private final AbstractModuleMetadataCache writableCache;

    public TwoStageModuleMetadataCache(BuildCommencedTimeProvider timeProvider, AbstractModuleMetadataCache readOnlyCache, AbstractModuleMetadataCache writableCache) {
        super(timeProvider);
        this.readOnlyCache = readOnlyCache;
        this.writableCache = writableCache;
    }

    @Override
    protected CachedMetadata store(ModuleComponentAtRepositoryKey key, ModuleMetadataCacheEntry entry, CachedMetadata cachedMetaData) {
        writableCache.store(key, entry, cachedMetaData);
        return cachedMetaData;
    }

    @Override
    protected CachedMetadata get(ModuleComponentAtRepositoryKey key) {
        CachedMetadata writeEntry = writableCache.get(key);
        if (writeEntry != null) {
            return writeEntry;
        }
        return readOnlyCache.get(key);
    }
}
