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

import org.gradle.StartParameter;
import org.gradle.cache.CacheRepository;
import org.gradle.caching.BuildCache;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.util.SingleMessageLogger;

import java.io.File;

public class DefaultBuildCacheConfiguration implements BuildCacheConfigurationInternal, Stoppable {
    private final boolean pullAllowed;
    private final boolean pushAllowed;
    private final CacheRepository cacheRepository;
    private final StartParameter startParameter;
    private BuildCacheFactory factory;
    private BuildCache cache;

    public DefaultBuildCacheConfiguration(CacheRepository cacheRepository, StartParameter startParameter) {
        this.cacheRepository = cacheRepository;
        this.startParameter = startParameter;
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
        this.factory = factory;
    }

    @Override
    public BuildCache getCache() {
        // TODO:LPTR Instantiate this as a service instead
        if (cache == null) {
            this.cache = new LenientBuildCacheDecorator(
                new ShortCircuitingErrorHandlerBuildCacheDecorator(3,
                    new LoggingBuildCacheDecorator(
                            factory.createCache(startParameter))));
            if (isPullAllowed() && isPushAllowed()) {
                SingleMessageLogger.incubatingFeatureUsed("Using " + cache.getDescription());
            } else if (isPushAllowed()) {
                SingleMessageLogger.incubatingFeatureUsed("Pushing task output to " + cache.getDescription());
            } else if (isPullAllowed()) {
                SingleMessageLogger.incubatingFeatureUsed("Retrieving task output from " + cache.getDescription());
            }
            // else, TODO: Misconfiguration? neither push nor pull is allowed
        }
        return cache;
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
        CompositeStoppable.stoppable(cache).stop();
    }
}
