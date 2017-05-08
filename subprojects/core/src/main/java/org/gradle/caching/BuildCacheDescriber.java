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
import org.gradle.api.Nullable;
import org.gradle.caching.configuration.BuildCache;

/**
 * Builder-style object that allows {@link BuildCacheServiceFactory} implementations to describe the cache.
 * <p>
 * The description is for human consumption.
 * It may be logged and displayed by tooling.
 *
 * @see BuildCacheServiceFactory#createBuildCacheService(BuildCache, BuildCacheDescriber)
 * @since 4.0
 */
@Incubating
public interface BuildCacheDescriber {

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
    BuildCacheDescriber type(String type);

    /**
     * Sets a configuration param of the cache being used.
     * <p>
     * e.g. {@code config("location", "https://some/cache")}.
     * <p>
     * Values may be logged.
     * Secrets (e.g. passwords) should not be declared with this method.
     * <p>
     * Describing config is not required.
     * Implementations should describe their config where possible.
     * <p>
     * Subsequent calls to this method with the same {@code name} argument will replace
     * the previously supplied {@code value} argument.
     * <p>
     * Subsequent calls to this method with different {@code name} arguments will append values.
     */
    BuildCacheDescriber config(String name, @Nullable String value);

}
