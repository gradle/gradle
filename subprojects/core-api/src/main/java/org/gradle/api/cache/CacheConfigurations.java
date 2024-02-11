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

package org.gradle.api.cache;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.internal.HasInternalProtocol;

/**
 * Configures caches stored in the user home directory.  Note that these values can be read at any time,
 * but can only be configured via an init script, ideally stored in the init.d directory in the user home
 * directory.
 *
 * @since 8.0
 */
@HasInternalProtocol
@Incubating
public interface CacheConfigurations {

    /**
     * Configures caching for wrapper distributions that are released Gradle versions.  By default, released
     * distributions are removed after 30 days of not being used.
     */
    void releasedWrappers(Action<? super CacheResourceConfiguration> cacheConfiguration);

    /**
     * Returns the cache configuration for wrapper distributions that are released Gradle versions.
     */
    CacheResourceConfiguration getReleasedWrappers();

    /**
     * Configures caching for wrapper distributions that are snapshot Gradle versions.  By default, snapshot
     * distributions are removed after 7 days of not being used.
     */
    void snapshotWrappers(Action<? super CacheResourceConfiguration> cacheConfiguration);

    /**
     * Returns the cache configuration for wrapper distributions that are released Gradle versions.
     */
    CacheResourceConfiguration getSnapshotWrappers();

    /**
     * Configures caching for resources that are downloaded during Gradle builds.  By default, downloaded
     * resources are removed after 30 days of not being used.
     */
    void downloadedResources(Action<? super CacheResourceConfiguration> cacheConfiguration);

    /**
     * Returns the cache configuration for downloaded resources.
     */
    CacheResourceConfiguration getDownloadedResources();

    /**
     * Configures caching for resources that are created by Gradle during the course of a build.  By default, created
     * resources are removed after 7 days of not being used.
     */
    void createdResources(Action<? super CacheResourceConfiguration> cacheConfiguration);

    /**
     * Returns the cache configuration for created resources.
     */
    CacheResourceConfiguration getCreatedResources();

    /**
     * Returns the cache cleanup settings that apply to all caches.
     */
    Property<Cleanup> getCleanup();

    /**
     * Configures how caches should be marked, if at all.
     *
     * <p>
     * By default, caches are marked using {@link MarkingStrategy#CACHEDIR_TAG}.
     * </p>
     *
     * @since 8.1
     */
    Property<MarkingStrategy> getMarkingStrategy();

}
