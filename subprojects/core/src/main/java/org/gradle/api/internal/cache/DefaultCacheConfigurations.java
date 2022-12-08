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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.cache.CleanupFrequency;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import java.util.function.Supplier;

import static org.gradle.internal.time.TimestampSuppliers.daysAgo;

abstract public class DefaultCacheConfigurations implements CacheConfigurationsInternal {
    private static final String RELEASED_WRAPPERS = "releasedWrappers";
    private static final String SNAPSHOT_WRAPPERS = "snapshotWrappers";
    private static final String DOWNLOADED_RESOURCES = "downloadedResources";
    private static final String CREATED_RESOURCES = "createdResources";
    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();

    private CacheResourceConfigurationInternal releasedWrappersConfiguration;
    private CacheResourceConfigurationInternal snapshotWrappersConfiguration;
    private CacheResourceConfigurationInternal downloadedResourcesConfiguration;
    private CacheResourceConfigurationInternal createdResourcesConfiguration;
    private UnlockableProperty<Cleanup> cleanup;

    @Inject
    public DefaultCacheConfigurations(ObjectFactory objectFactory, PropertyHost propertyHost) {
        this.releasedWrappersConfiguration = createResourceConfiguration(objectFactory, RELEASED_WRAPPERS, DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS);
        this.snapshotWrappersConfiguration = createResourceConfiguration(objectFactory, SNAPSHOT_WRAPPERS, DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS);
        this.downloadedResourcesConfiguration = createResourceConfiguration(objectFactory, DOWNLOADED_RESOURCES, DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES);
        this.createdResourcesConfiguration = createResourceConfiguration(objectFactory, CREATED_RESOURCES, DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES);
        this.cleanup = new DefaultUnlockableProperty<>(propertyHost, Cleanup.class, "cleanup").convention(Cleanup.DEFAULT);
        lockValues();
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
    public UnlockableProperty<Cleanup> getCleanup() {
        return cleanup;
    }

    @Override
    public void setCleanup(UnlockableProperty<Cleanup> cleanup) {
        this.cleanup = cleanup;
    }

    @Override
    public Provider<CleanupFrequency> getCleanupFrequency() {
        return getCleanup().map(cleanup -> ((CleanupInternal)cleanup).getCleanupFrequency());
    }

    @Override
    public void withMutableValues(Runnable runnable) {
        unlockValues();
        try {
            runnable.run();
        } finally {
            lockValues();
        }
    }

    private void lockValues() {
        releasedWrappersConfiguration.getRemoveUnusedEntriesOlderThan().lock();
        snapshotWrappersConfiguration.getRemoveUnusedEntriesOlderThan().lock();
        downloadedResourcesConfiguration.getRemoveUnusedEntriesOlderThan().lock();
        createdResourcesConfiguration.getRemoveUnusedEntriesOlderThan().lock();
        getCleanup().lock();
    }

    private void unlockValues() {
        releasedWrappersConfiguration.getRemoveUnusedEntriesOlderThan().unlock();
        snapshotWrappersConfiguration.getRemoveUnusedEntriesOlderThan().unlock();
        downloadedResourcesConfiguration.getRemoveUnusedEntriesOlderThan().unlock();
        createdResourcesConfiguration.getRemoveUnusedEntriesOlderThan().unlock();
        getCleanup().unlock();
    }

    private static <T> Provider<T> providerFromSupplier(Supplier<T> supplier) {
        return new DefaultProvider<>(supplier::get);
    }

    static abstract class DefaultCacheResourceConfiguration implements CacheResourceConfigurationInternal {
        private final String name;
        private final UnlockableProperty<Long> removeUnusedEntriesOlderThan;

        @Inject
        public DefaultCacheResourceConfiguration(PropertyHost propertyHost, String name) {
            this.name = name;
            this.removeUnusedEntriesOlderThan = new DefaultUnlockableProperty<>(propertyHost, Long.class, "removeUnusedEntriesOlderThan");
        }

        @Override
        public UnlockableProperty<Long> getRemoveUnusedEntriesOlderThan() {
            return removeUnusedEntriesOlderThan;
        }

        /**
         * @implNote Returns a supplier mapped from the property.  This provides a supplier that is resilient
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

    @NotThreadSafe
    static class DefaultUnlockableProperty<T> extends DefaultProperty<T> implements UnlockableProperty<T> {
        private final String displayName;
        private boolean mutable = true;

        public DefaultUnlockableProperty(PropertyHost propertyHost, Class<T> type, String displayName) {
            super(propertyHost, type);
            this.displayName = displayName;
        }

        public void lock() {
            mutable = false;
        }

        public void unlock() {
            mutable = true;
        }

        private IllegalStateException lockedError() {
            return new IllegalStateException("You can only configure the property '" + getDisplayName() + "' in an init script, preferably stored in the init.d directory inside the Gradle user home directory. See " + DOCUMENTATION_REGISTRY.getDocumentationFor("directory_layout", "dir:gradle_user_home:configure_cache_cleanup") + " for more information.");
        }

        private void onlyIfMutable(Runnable runnable) {
            if (mutable) {
                runnable.run();
            } else {
                throw lockedError();
            }
        }

        @Override
        protected DisplayName getDisplayName() {
            if (displayName != null) {
                return Describables.of(displayName);
            } else {
                return super.getDisplayName();
            }
        }

        @Override
        public void set(@Nullable T value) {
            onlyIfMutable(() -> super.set(value));
        }

        @Override
        public void set(Provider<? extends T> provider) {
            onlyIfMutable(() -> super.set(provider));
        }

        @Override
        public DefaultUnlockableProperty<T> value(@Nullable T value) {
            onlyIfMutable(() -> super.value(value));
            return this;
        }

        @Override
        public DefaultUnlockableProperty<T> value(Provider<? extends T> provider) {
            onlyIfMutable(() -> super.value(provider));
            return this;
        }

        @Override
        public DefaultUnlockableProperty<T> convention(@Nullable T value) {
            onlyIfMutable(() -> super.convention(value));
            return this;
        }

        @Override
        public DefaultUnlockableProperty<T> convention(Provider<? extends T> provider) {
            onlyIfMutable(() -> super.convention(provider));
            return this;
        }
    }
}
