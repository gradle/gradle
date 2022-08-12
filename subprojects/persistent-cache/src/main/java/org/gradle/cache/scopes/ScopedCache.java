/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.cache.scopes;

import org.gradle.cache.CacheBuilder;

import java.io.File;

public interface ScopedCache {
    /**
     * Creates a Gradle-version specific cache in this scope. See {@link org.gradle.cache.CacheRepository#cache(String)}.
     *
     * @param key A unique name for the cache.
     */
    CacheBuilder cache(String key);

    /**
     * Creates a cross Gradle version cache in this scope. See {@link org.gradle.cache.CacheRepository#cache(String)}.
     *
     * @param key A unique name for the cache.
     */
    CacheBuilder crossVersionCache(String key);

    /**
     * Returns the root directory of this cache. You should avoid using this method and instead use one of the other methods.
     */
    File getRootDir();

    /**
     * Returns the base directory that would be used for a Gradle-version specific cache om this scope.
     */
    File baseDirForCache(String key);

    /**
     * Returns the base directory that would be used for a cross Gradle version specific cache om this scope.
     */
    File baseDirForCrossVersionCache(String key);
}
