/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.StartParameter;
import org.gradle.cache.CacheRepository;
import org.gradle.caching.BuildCache;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;

import java.io.File;
import java.util.List;

public class DefaultBuildCacheConfiguration implements BuildCacheConfigurationInternal, Stoppable {
    private final boolean pullAllowed;
    private final boolean pushAllowed;
    private final CacheRepository cacheRepository;
    private final List<BuildCache> cachesCreated = Lists.newCopyOnWriteArrayList();
    private BuildCacheFactory factory;

    public DefaultBuildCacheConfiguration(CacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
        useLocalCache();
        this.pullAllowed = "true".equalsIgnoreCase(System.getProperty("org.gradle.cache.tasks.pull", "true").trim());
        this.pushAllowed = "true".equalsIgnoreCase(System.getProperty("org.gradle.cache.tasks.push", "true").trim());
    }

    @Override
    public void useLocalCache() {
        setFactory(new BuildCacheFactory() {
            @Override
            public BuildCache createCache(StartParameter startParameter) {
                String cacheDirectoryPath = System.getProperty("org.gradle.cache.tasks.directory");
                return cacheDirectoryPath != null
                    ? new LocalDirectoryBuildCache(cacheRepository, new File(cacheDirectoryPath))
                    : new LocalDirectoryBuildCache(cacheRepository, "task-cache");
            }
        });
    }

    @Override
    public void useLocalCache(final File directory) {
        setFactory(new BuildCacheFactory() {
            @Override
            public BuildCache createCache(StartParameter startParameter) {
                return new LocalDirectoryBuildCache(cacheRepository, directory);
            }
        });
    }

    @Override
    public void useCacheFactory(BuildCacheFactory factory) {
        setFactory(factory);
    }

    private void setFactory(final BuildCacheFactory factory) {
        this.factory = new BuildCacheFactory() {
            @Override
            public BuildCache createCache(StartParameter startParameter) {
                BuildCache cache = factory.createCache(startParameter);
                cachesCreated.add(cache);
                return cache;
            }
        };
    }

    @Override
    public BuildCacheFactory getCacheFactory() {
        return factory;
    }

    @Override
    public boolean isPullAllowed() {
        return pullAllowed;
    }

    @Override
    public boolean isPushAllowed() {
        return pushAllowed;
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(cachesCreated).stop();
    }
}
