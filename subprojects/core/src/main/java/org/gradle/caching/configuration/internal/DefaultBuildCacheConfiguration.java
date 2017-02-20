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
import org.gradle.api.specs.Spec;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.local.LocalBuildCache;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class DefaultBuildCacheConfiguration implements BuildCacheConfigurationInternal {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheConfiguration.class);

    private final Instantiator instantiator;
    private final LocalBuildCache local;
    private BuildCache remote;

    private final Map<Class<? extends BuildCache>, BuildCacheServiceFactory<?>> factories;

    public DefaultBuildCacheConfiguration(Instantiator instantiator, List<BuildCacheServiceFactory> allBuildCacheServiceFactories) {
        this.instantiator = instantiator;
        this.factories = Maps.newHashMap();
        this.local = createBuildCacheConfiguration(LocalBuildCache.class);
        // Register any built-in factories
        for (BuildCacheServiceFactory buildCacheServiceFactory : allBuildCacheServiceFactories) {
            registerBuildCacheServiceFactory(buildCacheServiceFactory);
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
        if (remote == null) {
            this.remote = createBuildCacheConfiguration(type);
        } else if (!type.isInstance(remote)) {
            // Type is not the same, fail
            throw new IllegalArgumentException(String.format("The given remote build cache type '%s' does not match the already configured type '%s'.", type.getName(), remote.getClass().getSuperclass().getCanonicalName()));
        }
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
    public void registerBuildCacheServiceFactory(BuildCacheServiceFactory<?> buildCacheServiceFactory) {
        Preconditions.checkNotNull(buildCacheServiceFactory, "You cannot register a null build cache service factory.");
        factories.put(buildCacheServiceFactory.getConfigurationType(), buildCacheServiceFactory);
    }

    @Override
    public <T extends BuildCache> BuildCacheServiceFactory<T> getFactory(final Class<? extends T> buildCacheType) {
        BuildCacheServiceFactory<?> factory = CollectionUtils.findFirst(factories.values(),
            new Spec<BuildCacheServiceFactory<?>>() {
                @Override
                public boolean isSatisfiedBy(BuildCacheServiceFactory<?> factory) {
                    return factory.getConfigurationType().isAssignableFrom(buildCacheType);
                }
            });
        if (factory == null) {
            throw new IllegalArgumentException(String.format("No build cache service factory for type '%s' could be found. Factories are known for %s.", buildCacheType.getSuperclass().getCanonicalName(), factories.keySet()));
        }

        LOGGER.info("Loaded {} factory implementation {}", buildCacheType.getCanonicalName(), factory.getClass().getCanonicalName());
        return Cast.uncheckedCast(factory);
    }
}
