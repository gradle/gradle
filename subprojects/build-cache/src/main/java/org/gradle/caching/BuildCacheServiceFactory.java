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

import org.gradle.caching.configuration.BuildCache;

/**
 * Factory interface to be provided by build cache service implementations.
 *
 * <p>
 * To be able to use a {@code BuildCacheService}, the factory that implements this interface
 * and the configuration type ({@link BuildCache}) must be
 * registered with the {@link org.gradle.caching.configuration.BuildCacheConfiguration}.
 * </p>
 * <p>
 * In {@literal settings.gradle}:
 * </p>
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
 *
 * @param <T> the type of build cache configuration this factory can handle.
 * @since 3.5
 */
public interface BuildCacheServiceFactory<T extends BuildCache> {

    /**
     * Creates a build cache service from the given configuration.
     *
     * Implementations should also provide a description via the given describer.
     */
    BuildCacheService createBuildCacheService(T configuration, Describer describer);

    /**
     * Builder-style object that allows build cache service factories to describe the cache service.
     * <p>
     * The description is for human consumption.
     * It may be logged and displayed by tooling.
     *
     * @since 4.0
     */
    interface Describer {

        /**
         * Sets the description of the type of cache being used.
         * <p>
         * The value should not include particulars about the cache; only a human friendly description of the kind of cache.
         * For example, instead of {@code "HTTP @ https://some/cache"} it should be just {@code "HTTP"}.
         * Particular configuration should be set via {@link #config(String, String)}.
         * <p>
         * {@link BuildCacheServiceFactory} implementations should always return the same value for the same cache “type”.
         * All implementations should call this method.
         * <p>
         * Values should be lowercase, except where using an acronym (e.g. HTTP).
         * <p>
         * Subsequent calls to this method replace the previously set value.
         */
        Describer type(String type);

        /**
         * Sets a configuration param of the cache being used.
         * <p>
         * e.g. {@code config("location", "https://some/cache")}.
         * <p>
         * Values may be logged.
         * Secrets (e.g. passwords) should not be declared with this method.
         * <p>
         * Implementations should describe their config where possible.
         * <p>
         * Subsequent calls to this method with the same {@code name} argument will replace
         * the previously supplied {@code value} argument.
         * <p>
         * Subsequent calls to this method with different {@code name} arguments will append values.
         */
        Describer config(String name, String value);

    }
}
