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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.local.LocalBuildCache;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class DefaultBuildCacheConfiguration implements BuildCacheConfigurationInternal {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheConfiguration.class);

    private final Instantiator instantiator;
    private final LocalBuildCache local;
    private BuildCache remote;

    private final Map<Class<? extends BuildCache>, Class<? extends BuildCacheServiceFactory<?>>> registeredTypes;

    public DefaultBuildCacheConfiguration(Instantiator instantiator, List<BuildCacheServiceRegistration> allBuiltInBuildCacheServices) {
        this.instantiator = instantiator;
        this.local = createBuildCacheConfiguration(LocalBuildCache.class);
        this.registeredTypes = Maps.newHashMap();

        // Register any built-in build cache types
        for (BuildCacheServiceRegistration builtInBuildCacheService : allBuiltInBuildCacheServices) {
            registerBuildCacheService(builtInBuildCacheService.getConfigurationType(), builtInBuildCacheService.getFactoryType());
        }
    }

    @Override
    public LocalBuildCache getLocal() {
        return local;
    }

    @Override
    public void local(Action<? super LocalBuildCache> configuration) {
        configuration.execute(local);
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type) {
        return remote(type, Actions.doNothing());
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type, Action<? super T> configuration) {
        if (remote != null) {
            LOGGER.debug("Replacing remote build cache type {} with {}", remote.getClass().getCanonicalName(), type.getCanonicalName());
        }
        this.remote = createBuildCacheConfiguration(type);
        T configurationObject = Cast.uncheckedCast(remote);
        configuration.execute(configurationObject);
        return configurationObject;
    }

    @Override
    public void remote(Action<? super BuildCache> configuration) {
        if (remote == null) {
            throw new IllegalStateException("A type for the remote build cache must be configured first.");
        }
        configuration.execute(remote);
    }

    @Override
    public BuildCache getRemote() {
        return remote;
    }

    private <T extends BuildCache> T createBuildCacheConfiguration(Class<T> type) {
        return instantiator.newInstance(type);
    }

    @Override
    public <T extends BuildCache> void registerBuildCacheService(Class<T> configurationType, Class<BuildCacheServiceFactory<T>> buildCacheServiceFactoryType) {
        Preconditions.checkNotNull(configurationType, "configurationType cannot be null.");
        Preconditions.checkNotNull(buildCacheServiceFactoryType, "buildCacheServiceFactoryType cannot be null.");
        registeredTypes.put(configurationType, buildCacheServiceFactoryType);
    }

    @Override
    public <T extends BuildCache> Class<? extends BuildCacheServiceFactory<T>> getBuildCacheServiceFactoryType(Class<T> configurationType) {
        for (Map.Entry<Class<? extends BuildCache>, Class<? extends BuildCacheServiceFactory<?>>> registration : registeredTypes.entrySet()) {
            Class<? extends BuildCache> registeredType = registration.getKey();
            if (registeredType.isAssignableFrom(configurationType)) {
                Class<? extends BuildCacheServiceFactory<?>> buildCacheServiceFactoryType = registration.getValue();
                LOGGER.info("Found {} registered for {}", buildCacheServiceFactoryType, registeredType);
                return Cast.uncheckedCast(buildCacheServiceFactoryType);
            }
        }

        // Couldn't find a registration for the given type
        throw new IllegalArgumentException(String.format("No build cache service factory for configuration type '%s' could be found.", configurationType.getSuperclass().getCanonicalName()));
    }
}
