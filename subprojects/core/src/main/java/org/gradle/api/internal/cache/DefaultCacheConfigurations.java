/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.cache;

import org.gradle.api.Action;
import org.gradle.api.cache.CacheResourceConfiguration;
import org.gradle.api.cache.Cleanup;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupFrequency;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.cache.internal.GradleUserHomeCacheCleanupActionDecorator;
import org.gradle.internal.cache.MonitoredCleanupAction;
import org.gradle.initialization.GradleUserHomeDirProvider;

import javax.inject.Inject;
import java.util.function.Supplier;

import static org.gradle.internal.time.TimestampSuppliers.daysAgo;

abstract public class DefaultCacheConfigurations implements CacheConfigurationsInternal {
    private final GradleUserHomeCacheCleanupActionDecorator delegate;

    private CacheResourceConfigurationInternal releasedWrappersConfiguration;
    private CacheResourceConfigurationInternal snapshotWrappersConfiguration;
    private CacheResourceConfigurationInternal downloadedResourcesConfiguration;
    private CacheResourceConfigurationInternal createdResourcesConfiguration;
    private Property<Cleanup> cleanup;

    @Inject
    public DefaultCacheConfigurations(ObjectFactory objectFactory, GradleUserHomeDirProvider gradleUserHomeDirProvider) {
        this.releasedWrappersConfiguration = createResourceConfiguration(objectFactory, "releasedWrappers", DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS);
        this.snapshotWrappersConfiguration = createResourceConfiguration(objectFactory, "snapshotWrappers", DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS);
        this.downloadedResourcesConfiguration = createResourceConfiguration(objectFactory, "downloadedResources", DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES);
        this.createdResourcesConfiguration = createResourceConfiguration(objectFactory, "createdResources", DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES);
        this.cleanup = objectFactory.property(Cleanup.class).convention(Cleanup.DEFAULT);
        this.delegate = new GradleUserHomeCacheCleanupActionDecorator(gradleUserHomeDirProvider);
    }

    private static CacheResourceConfigurationInternal createResourceConfiguration(ObjectFactory objectFactory, String name, int defaultDays) {
        CacheResourceConfigurationInternal resourceConfiguration = objectFactory.newInstance(DefaultCacheResourceConfiguration.class, name);
        resourceConfiguration.getRemoveUnusedEntriesOlderThan().convention(providerFromSupplier(daysAgo(defaultDays)));
        return resourceConfiguration;
    }

    @Override
    public void releasedWrappers(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(releasedWrappersConfiguration);
    }

    @Override
    public CacheResourceConfigurationInternal getReleasedWrappers() {
        return releasedWrappersConfiguration;
    }

    @Override
    public void setReleasedWrappers(CacheResourceConfigurationInternal releasedWrappers) {
        this.releasedWrappersConfiguration = releasedWrappers;
    }

    @Override
    public void snapshotWrappers(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(snapshotWrappersConfiguration);
    }

    @Override
    public CacheResourceConfigurationInternal getSnapshotWrappers() {
        return snapshotWrappersConfiguration;
    }

    @Override
    public void setSnapshotWrappers(CacheResourceConfigurationInternal snapshotWrappers) {
        this.snapshotWrappersConfiguration = snapshotWrappers;
    }

    @Override
    public void downloadedResources(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(downloadedResourcesConfiguration);
    }

    @Override
    public CacheResourceConfigurationInternal getDownloadedResources() {
        return downloadedResourcesConfiguration;
    }

    @Override
    public void setDownloadedResources(CacheResourceConfigurationInternal downloadedResources) {
        this.downloadedResourcesConfiguration = downloadedResources;
    }

    @Override
    public void createdResources(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(createdResourcesConfiguration);
    }

    @Override
    public CacheResourceConfigurationInternal getCreatedResources() {
        return createdResourcesConfiguration;
    }

    @Override
    public void setCreatedResources(CacheResourceConfigurationInternal createdResources) {
        this.createdResourcesConfiguration = createdResources;
    }

    @Override
    public Property<Cleanup> getCleanup() {
        return cleanup;
    }

    @Override
    public void setCleanup(Property<Cleanup> cleanup) {
        this.cleanup = cleanup;
    }

    @Override
    public Provider<CleanupFrequency> getCleanupFrequency() {
        return getCleanup().map(cleanup -> ((CleanupInternal)cleanup).getCleanupFrequency());
    }

    @Override
    public void finalizeConfigurations() {
        releasedWrappersConfiguration.getRemoveUnusedEntriesOlderThan().finalizeValue();
        snapshotWrappersConfiguration.getRemoveUnusedEntriesOlderThan().finalizeValue();
        downloadedResourcesConfiguration.getRemoveUnusedEntriesOlderThan().finalizeValue();
        createdResourcesConfiguration.getRemoveUnusedEntriesOlderThan().finalizeValue();
        getCleanup().finalizeValue();
    }

    @Override
    public MonitoredCleanupAction decorate(MonitoredCleanupAction cleanupAction) {
        MonitoredCleanupAction decoratedCleanupAction = delegate.decorate(cleanupAction);
        return new MonitoredCleanupAction() {
            @Override
            public boolean execute(CleanupProgressMonitor progressMonitor) {
                return isEnabled() && decoratedCleanupAction.execute(progressMonitor);
            }

            @Override
            public String getDisplayName() {
                return decoratedCleanupAction.getDisplayName();
            }
        };
    }

    @Override
    public CleanupAction decorate(CleanupAction cleanupAction) {
        CleanupAction decoratedCleanupAction = delegate.decorate(cleanupAction);
        return (cleanableStore, progressMonitor) -> {
            if (isEnabled()) {
                decoratedCleanupAction.clean(cleanableStore, progressMonitor);
            }
        };
    }

    private boolean isEnabled() {
        return getCleanupFrequency().get() != CleanupFrequency.NEVER;
    }

    private static <T> Provider<T> providerFromSupplier(Supplier<T> supplier) {
        return new DefaultProvider<>(supplier::get);
    }

    static abstract class DefaultCacheResourceConfiguration implements CacheResourceConfigurationInternal {
        private final String name;

        @Inject
        public DefaultCacheResourceConfiguration(String name) {
            this.name = name;
        }

        /**
         * Returns a supplier mapped from the property.  This provides a supplier that is resilient
         * to subsequent changes to the property value as opposed to just calling get() on the property.
         */
        @Override
        public Supplier<Long> getRemoveUnusedEntriesOlderThanAsSupplier() {
            return () -> getRemoveUnusedEntriesOlderThan().get();
        }


        @Override
        public void setRemoveUnusedEntriesAfterDays(int removeUnusedEntriesAfterDays) {
            if (removeUnusedEntriesAfterDays < 1) {
                throw new IllegalArgumentException(name + " cannot be set to retain entries for " + removeUnusedEntriesAfterDays + " days.  For time frames shorter than one day, use the 'removeUnusedEntriesOlderThan' property.");
            }
            getRemoveUnusedEntriesOlderThan().set(providerFromSupplier(daysAgo(removeUnusedEntriesAfterDays)));
        }
    }
}
