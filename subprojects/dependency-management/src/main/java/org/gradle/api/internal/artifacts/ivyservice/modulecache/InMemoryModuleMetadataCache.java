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

import com.google.common.collect.Maps;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.Map;

public class InMemoryModuleMetadataCache extends AbstractModuleMetadataCache {
    private final Map<ModuleComponentAtRepositoryKey, CachedMetadata> inMemoryCache = Maps.newConcurrentMap();
    private final AbstractModuleMetadataCache delegate;

    public InMemoryModuleMetadataCache(BuildCommencedTimeProvider timeProvider) {
        super(timeProvider);
        delegate = null;
    }

    public InMemoryModuleMetadataCache(BuildCommencedTimeProvider timeProvider, AbstractModuleMetadataCache delegate) {
        super(timeProvider);
        this.delegate = delegate;
    }

    @Override
    protected CachedMetadata get(ModuleComponentAtRepositoryKey key) {
        CachedMetadata metadata = inMemoryCache.get(key);
        if (metadata == null && delegate != null) {
            metadata = delegate.get(key);
            if (metadata != null) {
                inMemoryCache.put(key, metadata);
            }
        }
        return metadata;
    }

    @Override
    protected CachedMetadata store(ModuleComponentAtRepositoryKey key, ModuleMetadataCacheEntry entry, CachedMetadata cachedMetaData) {
        CachedMetadata dehydrated = cachedMetaData.dehydrate();
        inMemoryCache.put(key, dehydrated);
        if (delegate != null) {
            delegate.store(key, entry, dehydrated);
        }
        return dehydrated;
    }

}
