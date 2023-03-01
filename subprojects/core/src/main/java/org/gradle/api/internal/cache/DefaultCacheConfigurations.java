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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Action;
import org.gradle.api.cache.CacheResourceConfiguration;
import org.gradle.api.cache.Cleanup;
import org.gradle.api.cache.MarkingStrategy;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.cache.CleanupFrequency;
import org.gradle.cache.internal.LegacyCacheCleanupEnablement;
import org.gradle.cache.internal.WrapperDistributionCleanupAction;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.function.Supplier;

import static org.gradle.internal.time.TimestampSuppliers.daysAgo;

abstract public class DefaultCacheConfigurations implements CacheConfigurationsInternal {
    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();
    private static final String RELEASED_WRAPPERS = "releasedWrappers";
    private static final String SNAPSHOT_WRAPPERS = "snapshotWrappers";
    private static final String DOWNLOADED_RESOURCES = "downloadedResources";
    private static final String CREATED_RESOURCES = "createdResources";
    static final String UNSAFE_MODIFICATION_ERROR = "The property '%s' was modified from an unsafe location (for instance a settings script or plugin).  This property can only be changed in an init script, preferably stored in the init.d directory inside the Gradle user home directory. See " + DOCUMENTATION_REGISTRY.getDocumentationFor("directory_layout", "dir:gradle_user_home:configure_cache_cleanup") + " for more information.";

    private final CacheResourceConfigurationInternal releasedWrappersConfiguration;
    private final CacheResourceConfigurationInternal snapshotWrappersConfiguration;
    private final CacheResourceConfigurationInternal downloadedResourcesConfiguration;
    private final CacheResourceConfigurationInternal createdResourcesConfiguration;
    private final Property<Cleanup> cleanup;
    private final Property<MarkingStrategy> markingStrategy;
    private final LegacyCacheCleanupEnablement legacyCacheCleanupEnablement;

    private boolean cleanupHasBeenConfigured;

    @Inject
    public DefaultCacheConfigurations(ObjectFactory objectFactory, PropertyHost propertyHost, LegacyCacheCleanupEnablement legacyCacheCleanupEnablement) {
        this.releasedWrappersConfiguration = createResourceConfiguration(objectFactory, RELEASED_WRAPPERS, DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS);
        this.snapshotWrappersConfiguration = createResourceConfiguration(objectFactory, SNAPSHOT_WRAPPERS, DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS);
        this.downloadedResourcesConfiguration = createResourceConfiguration(objectFactory, DOWNLOADED_RESOURCES, DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES);
        this.createdResourcesConfiguration = createResourceConfiguration(objectFactory, CREATED_RESOURCES, DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES);
        this.cleanup = new ContextualErrorMessageProperty<>(propertyHost, Cleanup.class, "cleanup").convention(createCleanupConvention());
        this.markingStrategy = new ContextualErrorMessageProperty<>(propertyHost, MarkingStrategy.class, "markingStrategy").convention(MarkingStrategy.CACHEDIR_TAG);
        this.legacyCacheCleanupEnablement = legacyCacheCleanupEnablement;
    }

    private static CacheResourceConfigurationInternal createResourceConfiguration(ObjectFactory objectFactory, String name, int defaultDays) {
        CacheResourceConfigurationInternal resourceConfiguration = objectFactory.newInstance(DefaultCacheResourceConfiguration.class, name);
        resourceConfiguration.getRemoveUnusedEntriesOlderThan().convention(providerFromSupplier(daysAgo(defaultDays)));
        return resourceConfiguration;
    }

    private Provider<Cleanup> createCleanupConvention() {
        return providerFromSupplier(() -> legacyCacheCleanupEnablement.isDisabledByProperty() ? Cleanup.DISABLED : Cleanup.DEFAULT);
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
    public void snapshotWrappers(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(snapshotWrappersConfiguration);
    }

    @Override
    public CacheResourceConfigurationInternal getSnapshotWrappers() {
        return snapshotWrappersConfiguration;
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
    public void createdResources(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(createdResourcesConfiguration);
    }

    @Override
    public CacheResourceConfigurationInternal getCreatedResources() {
        return createdResourcesConfiguration;
    }

    @Override
    public Property<Cleanup> getCleanup() {
        return cleanup;
    }

    @Override
    public Property<MarkingStrategy> getMarkingStrategy() {
        return markingStrategy;
    }

    @Override
    public Provider<CleanupFrequency> getCleanupFrequency() {
        return getCleanup().map(cleanup ->
            new MustBeConfiguredCleanupFrequency(((CleanupInternal) cleanup).getCleanupFrequency())
        );
    }

    @Override
    public void synchronize(CacheConfigurationsInternal persistentCacheConfigurations) {
        persistentCacheConfigurations.getReleasedWrappers().getRemoveUnusedEntriesOlderThan().value(getReleasedWrappers().getRemoveUnusedEntriesOlderThan());
        persistentCacheConfigurations.getSnapshotWrappers().getRemoveUnusedEntriesOlderThan().value(getSnapshotWrappers().getRemoveUnusedEntriesOlderThan());
        persistentCacheConfigurations.getDownloadedResources().getRemoveUnusedEntriesOlderThan().value(getDownloadedResources().getRemoveUnusedEntriesOlderThan());
        persistentCacheConfigurations.getCreatedResources().getRemoveUnusedEntriesOlderThan().value(getCreatedResources().getRemoveUnusedEntriesOlderThan());
        persistentCacheConfigurations.getCleanup().value(getCleanup());
        persistentCacheConfigurations.getMarkingStrategy().value(getMarkingStrategy());
    }

    public void finalizeConfiguration(Gradle gradle) {
        finalizeConfigurationValues();
        markCacheDirectories(gradle);
    }

    @VisibleForTesting
    void finalizeConfigurationValues() {
        releasedWrappersConfiguration.getRemoveUnusedEntriesOlderThan().finalizeValue();
        snapshotWrappersConfiguration.getRemoveUnusedEntriesOlderThan().finalizeValue();
        downloadedResourcesConfiguration.getRemoveUnusedEntriesOlderThan().finalizeValue();
        createdResourcesConfiguration.getRemoveUnusedEntriesOlderThan().finalizeValue();
        getCleanup().finalizeValue();
        getMarkingStrategy().finalizeValue();
    }

    private void markCacheDirectories(Gradle gradle) {
        MarkingStrategy strategy = getMarkingStrategy().get();
        strategy.tryMarkCacheDirectory(new File(
            gradle.getGradleUserHomeDir(),
            WrapperDistributionCleanupAction.WRAPPER_DISTRIBUTION_FILE_PATH
        ));
        strategy.tryMarkCacheDirectory(new File(
            gradle.getGradleUserHomeDir(),
            "daemon"
        ));
        strategy.tryMarkCacheDirectory(new File(
            gradle.getGradleUserHomeDir(),
            "caches"
        ));
        strategy.tryMarkCacheDirectory(new File(
            gradle.getGradleUserHomeDir(),
            "jdks"
        ));
    }

    @Override
    public void setCleanupHasBeenConfigured(boolean hasBeenConfigured) {
        this.cleanupHasBeenConfigured = hasBeenConfigured;
    }

    private static <T> Provider<T> providerFromSupplier(Supplier<T> supplier) {
        return new DefaultProvider<>(supplier::get);
    }

    static abstract class DefaultCacheResourceConfiguration implements CacheResourceConfigurationInternal {
        private final String name;
        private final Property<Long> removeUnusedEntriesOlderThan;

        @Inject
        public DefaultCacheResourceConfiguration(PropertyHost propertyHost, String name) {
            this.name = name;
            this.removeUnusedEntriesOlderThan = new ContextualErrorMessageProperty<>(propertyHost, Long.class, "removeUnusedEntriesOlderThan");
        }

        @Override
        public Property<Long> getRemoveUnusedEntriesOlderThan() {
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

    /**
     * An implementation of {@link Property} that provides a contextualized error if the value is mutated after finalization.
     */
    private static class ContextualErrorMessageProperty<T> extends DefaultProperty<T> {
        private final String displayName;

        public ContextualErrorMessageProperty(PropertyHost propertyHost, Class<T> type, String displayName) {
            super(propertyHost, type);
            this.displayName = displayName;
        }

        private IllegalStateException alreadyFinalizedError() {
            return new IllegalStateException(String.format(UNSAFE_MODIFICATION_ERROR, getDisplayName()));
        }

        private void onlyIfMutable(Runnable runnable) {
            if (isFinalized()) {
                throw alreadyFinalizedError();
            } else {
                runnable.run();
            }
        }

        @Override
        @Nonnull
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
        public ContextualErrorMessageProperty<T> value(@Nullable T value) {
            onlyIfMutable(() -> super.value(value));
            return this;
        }

        @Override
        public ContextualErrorMessageProperty<T> value(Provider<? extends T> provider) {
            onlyIfMutable(() -> super.value(provider));
            return this;
        }

        @Override
        public ContextualErrorMessageProperty<T> convention(@Nullable T value) {
            onlyIfMutable(() -> super.convention(value));
            return this;
        }

        @Override
        public ContextualErrorMessageProperty<T> convention(Provider<? extends T> provider) {
            onlyIfMutable(() -> super.convention(provider));
            return this;
        }
    }

    private class MustBeConfiguredCleanupFrequency implements CleanupFrequency {
        private final CleanupFrequency configuredCleanupFrequency;

        public MustBeConfiguredCleanupFrequency(CleanupFrequency configuredCleanupFrequency) {
            this.configuredCleanupFrequency = configuredCleanupFrequency;
        }

        @Override
        public boolean shouldCleanupOnEndOfSession() {
            return configuredCleanupFrequency.shouldCleanupOnEndOfSession();
        }

        @Override
        public boolean requiresCleanup(long lastCleanupTimestamp) {
            return cleanupHasBeenConfigured && configuredCleanupFrequency.requiresCleanup(lastCleanupTimestamp);
        }
    }
}
