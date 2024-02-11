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

import org.gradle.api.cache.CacheConfigurations;
import org.gradle.api.cache.Cleanup;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.cache.CleanupFrequency;

public interface CacheConfigurationsInternal extends CacheConfigurations {
    int DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS = 30;
    int DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS = 7;
    int DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES = 30;
    int DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES = 7;

    @Override
    CacheResourceConfigurationInternal getReleasedWrappers();
    @Override
    CacheResourceConfigurationInternal getSnapshotWrappers();
    @Override
    CacheResourceConfigurationInternal getDownloadedResources();
    @Override
    CacheResourceConfigurationInternal getCreatedResources();

    @Override
    Property<Cleanup> getCleanup();

    Provider<CleanupFrequency> getCleanupFrequency();

    void finalizeConfiguration(Gradle gradle);

    /**
     * Synchronizes the property values of the provided cache configurations with those of this cache configuration
     * by setting the provided configuration's properties to be backed by the properties of this configuration.
     */
    void synchronize(CacheConfigurationsInternal cacheConfigurationsInternal);

    void setCleanupHasBeenConfigured(boolean hasBeenConfigured);
}
