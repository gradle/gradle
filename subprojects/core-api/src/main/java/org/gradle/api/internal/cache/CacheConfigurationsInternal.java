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
    UnlockableProperty<Cleanup> getCleanup();

    /**
     * Execute the provided runnable with all cache configuration properties unlocked and mutable.
     */
    void withMutableValues(Runnable runnable);

    Provider<CleanupFrequency> getCleanupFrequency();

    /**
     * Represents a property that can be locked, preventing any changes that mutate the value.
     * As opposed to finalization, the expectation is that the property may be unlocked
     * again in the future.  This allows properties that can only be changed during a certain
     * window of time.
     */
    interface UnlockableProperty<T> extends Property<T> {
        /**
         * Lock the property, preventing changes that mutate the value.
         */
        void lock();

        /**
         * Unlock the property, allowing changes that mutate the value.
         */
        void unlock();
    }
}
