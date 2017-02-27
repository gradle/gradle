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

package org.gradle.caching;

import org.gradle.api.Incubating;

/**
 * Factory interface to be provided by build cache service implementations.
 *
 * <p>
 * To be able to use a {@code BuildCacheService}, the factory that implements this interface
 * and the configuration type ({@link org.gradle.caching.configuration.BuildCache}) must be
 * registered with the {@link org.gradle.caching.configuration.BuildCacheConfiguration}.
 * </p>
 * <p>
 * In {@literal settings.gradle}:
 *
 * <pre>
 *     buildCache {
 *         // Register custom build cache implementation
 *         registerBuildCacheService(CustomBuildCache, CustomBuildCacheFactory)
 *
 *         remote(CustomBuildCache) {
 *             // configure custom build cache.
 *         }
 *     }
 * </pre>
 * </p>
 * @param <T> the type of build cache configuration this factory can handle.
 *
 * @since 3.5
 */
@Incubating
public interface BuildCacheServiceFactory<T extends org.gradle.caching.configuration.BuildCache> {
    /**
     * Creates a build cache service with the given configuration.
     */
    BuildCacheService createBuildCacheService(T configuration);
}
