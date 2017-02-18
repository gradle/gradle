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

package org.gradle.caching.configuration.internal;

import org.gradle.api.internal.DependencyInjectingServiceLoader;
import org.gradle.api.specs.Spec;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.BuildCacheServiceFactory;
import org.gradle.internal.Cast;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildCacheServiceFactoryRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheServiceFactoryRegistry.class);

    private final Iterable<BuildCacheServiceFactory> factories;

    public BuildCacheServiceFactoryRegistry(DependencyInjectingServiceLoader serviceLoader, ClassLoader classLoader) {
        this.factories = findFactories(serviceLoader, classLoader);
    }

    private static Iterable<BuildCacheServiceFactory> findFactories(DependencyInjectingServiceLoader serviceLoader, ClassLoader classLoader) {
        return serviceLoader.load(BuildCacheServiceFactory.class, classLoader);
    }

    public <T extends BuildCache> BuildCacheServiceFactory<T> getBuildCacheServiceFactory(final Class<T> type) {
        BuildCacheServiceFactory<?> factory = CollectionUtils.findFirst(factories, new Spec<BuildCacheServiceFactory>() {
            @Override
            public boolean isSatisfiedBy(BuildCacheServiceFactory factory) {
                return factory.getConfigurationType().isAssignableFrom(type);
            }
        });
        if (factory == null) {
            throw new IllegalArgumentException(String.format("No build cache service factory of type %s is known", type.getName()));
        }
        LOGGER.info("Loaded {} implementation {}", BuildCacheServiceFactory.class.getSimpleName(), factory.getClass().getName());
        return Cast.<BuildCacheServiceFactory<T>>uncheckedCast(factory);
    }
}
