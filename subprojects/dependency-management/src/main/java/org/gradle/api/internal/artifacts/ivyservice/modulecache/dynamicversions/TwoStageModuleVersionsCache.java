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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions;

import com.google.common.collect.Sets;
import org.gradle.util.internal.BuildCommencedTimeProvider;

public class TwoStageModuleVersionsCache extends AbstractModuleVersionsCache {
    private final AbstractModuleVersionsCache readOnlyCache;
    private final AbstractModuleVersionsCache writableCache;

    public TwoStageModuleVersionsCache(BuildCommencedTimeProvider timeProvider, AbstractModuleVersionsCache readOnlyCache, AbstractModuleVersionsCache writableCache) {
        super(timeProvider);
        this.readOnlyCache = readOnlyCache;
        this.writableCache = writableCache;
    }

    @Override
    protected void store(ModuleAtRepositoryKey key, ModuleVersionsCacheEntry entry) {
        writableCache.store(key, entry);
    }

    @Override
    protected ModuleVersionsCacheEntry get(ModuleAtRepositoryKey key) {
        ModuleVersionsCacheEntry roEntry = readOnlyCache.get(key);
        ModuleVersionsCacheEntry writableEntry = writableCache.get(key);
        if (roEntry == null) {
            return writableEntry;
        }
        if (writableEntry == null) {
            return roEntry;
        }
        return new ModuleVersionsCacheEntry(Sets.union(roEntry.moduleVersionListing, writableEntry.moduleVersionListing), writableEntry.createTimestamp);
    }
}
