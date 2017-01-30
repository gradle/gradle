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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.DependencyInjectingServiceLoader;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.BuildCacheServiceBuilder;
import org.gradle.caching.configuration.BuildCacheServiceFactory;
import org.gradle.internal.Cast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class BuildCacheServiceFactoryRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheServiceFactoryRegistry.class);

    private final Map<Class<? extends BuildCache>, BuildCacheServiceFactory<? extends BuildCache>> factories;

    public BuildCacheServiceFactoryRegistry(DependencyInjectingServiceLoader serviceLoader, ClassLoader classLoader) {
        this.factories = findFactories(serviceLoader, classLoader);
    }

    private static Map<Class<? extends BuildCache>, BuildCacheServiceFactory<? extends BuildCache>> findFactories(DependencyInjectingServiceLoader serviceLoader, ClassLoader classLoader) {
        ImmutableMap.Builder<Class<? extends BuildCache>, BuildCacheServiceFactory<? extends BuildCache>> registryBuilder = ImmutableMap.builder();
        Iterable<BuildCacheServiceFactory> serviceFactories = serviceLoader.load(BuildCacheServiceFactory.class, classLoader);
        for (BuildCacheServiceFactory<?> serviceFactory : serviceFactories) {
            Class<? extends BuildCache> configurationType = serviceFactory.getConfigurationType();
            registryBuilder.put(configurationType, serviceFactory);
            LOGGER.info("Loaded {} implementation {}", BuildCacheServiceFactory.class.getSimpleName(), serviceFactory.getClass().getName());
        }
        return registryBuilder.build();
    }

    public <T extends BuildCache> BuildCacheServiceBuilder<? extends T> createServiceBuilder(Class<T> type) {
        BuildCacheServiceFactory<?> factory = factories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException(String.format("No cache of type %s is known", type.getName()));
        }
        return Cast.<BuildCacheServiceFactory<T>>uncheckedCast(factory).createBuilder();
    }
}
