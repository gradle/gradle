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
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class DefaultBuildCacheConfiguration implements BuildCacheConfigurationInternal {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheConfiguration.class);

    private final Instantiator instantiator;

    private DirectoryBuildCache local;
    private BuildCache remote;

    private Set<BuildCacheServiceRegistration> registrations;

    public DefaultBuildCacheConfiguration(Instantiator instantiator, List<BuildCacheServiceRegistration> allBuiltInBuildCacheServices) {
        this.instantiator = instantiator;
        this.registrations = Sets.newHashSet(allBuiltInBuildCacheServices);
        this.local = createLocalCacheConfiguration(instantiator, registrations);
    }

    @Override
    public DirectoryBuildCache getLocal() {
        return local;
    }

    @Override
    public void setLocal(DirectoryBuildCache local) {
        this.local = local;
    }

    private <T extends DirectoryBuildCache> T localInternal(Class<T> type, Action<? super T> configuration) {
        if (!type.equals(DirectoryBuildCache.class)) {
            throw new IllegalArgumentException("Using a local build cache type other than " + DirectoryBuildCache.class.getSimpleName() + " is not allowed");
        }
        T configurationObject = Cast.uncheckedNonnullCast(local);
        configuration.execute(configurationObject);
        return configurationObject;
    }

    @Override
    public void local(Action<? super DirectoryBuildCache> configuration) {
        configuration.execute(local);
    }

    @Nullable
    @Override
    public BuildCache getRemote() {
        return remote;
    }

    @Override
    public void setRemote(@Nullable BuildCache remote) {
        this.remote = remote;
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type) {
        return remote(type, Actions.doNothing());
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type, Action<? super T> configuration) {
        if (!type.isInstance(remote)) {
            if (remote != null) {
                LOGGER.info("Replacing remote build cache type {} with {}", remote.getClass().getCanonicalName(), type.getCanonicalName());
            }
            remote = createRemoteCacheConfiguration(instantiator, type, registrations);
        }
        T configurationObject = Cast.uncheckedNonnullCast(remote);
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
    public Set<BuildCacheServiceRegistration> getRegistrations() {
        return registrations;
    }

    @Override
    public void setRegistrations(Set<BuildCacheServiceRegistration> registrations) {
        this.registrations = registrations;
    }

    private static DirectoryBuildCache createLocalCacheConfiguration(Instantiator instantiator, Set<BuildCacheServiceRegistration> registrations) {
        DirectoryBuildCache local = createBuildCacheConfiguration(instantiator, DirectoryBuildCache.class, registrations);
        // By default, we push to the local cache.
        local.getPush().set(true);
        return local;
    }

    private static <T extends BuildCache> T createRemoteCacheConfiguration(Instantiator instantiator, Class<T> type, Set<BuildCacheServiceRegistration> registrations) {
        T remote = createBuildCacheConfiguration(instantiator, type, registrations);
        // By default, we do not push to the remote cache.
        remote.getPush().set(false);
        return remote;
    }

    private static <T extends BuildCache> T createBuildCacheConfiguration(Instantiator instantiator, Class<T> type, Set<BuildCacheServiceRegistration> registrations) {
        // ensure type is registered
        getBuildCacheServiceFactoryType(type, registrations);
        return instantiator.newInstance(type);
    }

    @Override
    public <T extends BuildCache> void registerBuildCacheService(Class<T> configurationType, Class<? extends BuildCacheServiceFactory<? super T>> buildCacheServiceFactoryType) {
        Preconditions.checkNotNull(configurationType, "configurationType cannot be null.");
        Preconditions.checkNotNull(buildCacheServiceFactoryType, "buildCacheServiceFactoryType cannot be null.");
        registrations.add(new DefaultBuildCacheServiceRegistration(configurationType, buildCacheServiceFactoryType));
    }

    @Override
    public <T extends BuildCache> Class<? extends BuildCacheServiceFactory<T>> getBuildCacheServiceFactoryType(Class<T> configurationType) {
        return getBuildCacheServiceFactoryType(configurationType, registrations);
    }

    private static <T extends BuildCache> Class<? extends BuildCacheServiceFactory<T>> getBuildCacheServiceFactoryType(Class<T> configurationType, Set<BuildCacheServiceRegistration> registrations) {
        for (BuildCacheServiceRegistration registration : registrations) {
            Class<? extends BuildCache> registeredConfigurationType = registration.getConfigurationType();
            if (registeredConfigurationType.isAssignableFrom(configurationType)) {
                Class<? extends BuildCacheServiceFactory<?>> buildCacheServiceFactoryType = registration.getFactoryType();
                LOGGER.debug("Found {} registered for {}", buildCacheServiceFactoryType, registeredConfigurationType);
                return Cast.uncheckedNonnullCast(buildCacheServiceFactoryType);
            }
        }
        // Couldn't find a registration for the given type
        throw new GradleException("Build cache type '" + configurationType.getName() + "' has not been registered.");
    }
}
