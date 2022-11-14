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
import org.gradle.api.cache.CacheConfigurations;
import org.gradle.api.model.ObjectFactory;

public class DefaultCacheConfigurations implements CacheConfigurationsInternal {
    private final CacheConfigurations.CacheResourceConfiguration releasedWrappersConfiguration;
    private final CacheConfigurations.CacheResourceConfiguration snapshotWrappersConfiguration;
    private final CacheConfigurations.CacheResourceConfiguration downloadedResourcesConfiguration;
    private final CacheConfigurations.CacheResourceConfiguration createdResourcesConfiguration;

    public DefaultCacheConfigurations(ObjectFactory objectFactory) {
        this.releasedWrappersConfiguration = createResourceConfiguration(objectFactory, DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS);
        this.snapshotWrappersConfiguration = createResourceConfiguration(objectFactory, DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS);
        this.downloadedResourcesConfiguration = createResourceConfiguration(objectFactory, DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES);
        this.createdResourcesConfiguration = createResourceConfiguration(objectFactory, DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES);
    }

    private static CacheResourceConfiguration createResourceConfiguration(ObjectFactory objectFactory, int defaultDays) {
        CacheResourceConfiguration resourceConfiguration = objectFactory.newInstance(CacheResourceConfiguration.class);
        resourceConfiguration.getRemoveUnusedEntriesAfterDays().convention(defaultDays);
        return resourceConfiguration;
    }

    @Override
    public void releasedWrappers(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(releasedWrappersConfiguration);
    }

    @Override
    public CacheResourceConfiguration getReleasedWrappers() {
        return releasedWrappersConfiguration;
    }

    @Override
    public void snapshotWrappers(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(snapshotWrappersConfiguration);
    }

    @Override
    public CacheResourceConfiguration getSnapshotWrappers() {
        return snapshotWrappersConfiguration;
    }

    @Override
    public void downloadedResources(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(downloadedResourcesConfiguration);
    }

    @Override
    public CacheResourceConfiguration getDownloadedResources() {
        return downloadedResourcesConfiguration;
    }

    @Override
    public void createdResources(Action<? super CacheResourceConfiguration> cacheConfiguration) {
        cacheConfiguration.execute(createdResourcesConfiguration);
    }

    @Override
    public CacheResourceConfiguration getCreatedResources() {
        return createdResourcesConfiguration;
    }

    @Override
    public void finalizeConfigurations() {
        releasedWrappersConfiguration.getRemoveUnusedEntriesAfterDays().finalizeValue();
        snapshotWrappersConfiguration.getRemoveUnusedEntriesAfterDays().finalizeValue();
        downloadedResourcesConfiguration.getRemoveUnusedEntriesAfterDays().finalizeValue();
        createdResourcesConfiguration.getRemoveUnusedEntriesAfterDays().finalizeValue();
    }
}
