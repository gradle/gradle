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
import org.gradle.cache.UnscopedCacheBuilderFactory;

import java.io.File;

/**
 * A factory for creating {@link CacheBuilder}s that are scoped to a particular context.
 */
public interface ScopedCacheBuilderFactory {
    /**
     * Creates a builder for Gradle-version specific caches in this scope. See {@link UnscopedCacheBuilderFactory#cache(String)}.
     *
     * @param key A unique name for the cache.
     */
    CacheBuilder createCacheBuilder(String key);

    /**
     * Creates a builder for cross Gradle version caches in this scope. See {@link UnscopedCacheBuilderFactory#cache(String)}.
     *
     * @param key A unique name for the cache.
     */
    CacheBuilder createCrossVersionCacheBuilder(String key);

    /**
     * Returns the root directory of this cache builder factory. You should avoid using this method and instead use one of the other methods.
     */
    File getRootDir();

    /**
     * Returns the base directory that would be used for a Gradle-version specific cache builder created by this factory.
     */
    File baseDirForCache(String key);

    /**
     * Returns the base directory that would be used for a cross Gradle version cache builder created by this factory.
     */
    File baseDirForCrossVersionCache(String key);


    File lockDirForCache(String key);
    File lockDirForCrossVersionCache(String key);
}
