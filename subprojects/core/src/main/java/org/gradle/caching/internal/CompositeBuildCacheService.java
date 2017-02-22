/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

public class CompositeBuildCacheService implements BuildCacheService {
    private final BuildCacheService pushToCache;
    private final List<BuildCacheService> pullFromCaches;

    public static BuildCacheService create(@Nullable BuildCacheService pushToCache, List<BuildCacheService> pullFromCaches) {
        if (pushToCache != null) {
            Preconditions.checkArgument(pullFromCaches.contains(pushToCache),
                "pushToCache %s must be contained in pullFromCaches", pushToCache.getDescription());
        }
        if (pullFromCaches.size() == 1) {
            return CollectionUtils.single(pullFromCaches);
        }
        return new CompositeBuildCacheService(pushToCache, pullFromCaches);
    }

    private CompositeBuildCacheService(@Nullable BuildCacheService pushToCache, List<BuildCacheService> pullFromCaches) {
        this.pushToCache = pushToCache;
        this.pullFromCaches = pullFromCaches;
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        for (BuildCacheService pullFromCache : pullFromCaches) {
            if (pullFromCache.load(key, reader)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        if (pushToCache != null) {
            pushToCache.store(key, writer);
        }
    }

    @Override
    public String getDescription() {
        return Joiner.on(" and ").join(CollectionUtils.collect(pullFromCaches, new Transformer<String, BuildCacheService>() {
            @Override
            public String transform(BuildCacheService buildCacheService) {
                return decoratePushToCache(buildCacheService);
            }
        }));
    }

    private String decoratePushToCache(BuildCacheService buildCacheService) {
        String description = buildCacheService.getDescription();
        return buildCacheService == pushToCache ? description + "(pushing enabled)" : description;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(pullFromCaches).stop();
    }
}
