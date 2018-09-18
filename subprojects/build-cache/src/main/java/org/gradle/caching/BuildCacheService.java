/*
 * Copyright 2016 the original author or authors.
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

import java.io.Closeable;
import java.io.IOException;

/**
 * Protocol interface to be implemented by a client to a build cache backend.
 *
 * <p>
 *     Build cache implementations should report a non-fatal failure as a {@link BuildCacheException}.
 *     Non-fatal failures could include failing to retrieve a cache entry or unsuccessfully completing an upload a new cache entry.
 *     Gradle will not fail the build when catching a {@code BuildCacheException}, but it may disable caching for the build if too
 *     many failures occur.
 * </p>
 * <p>
 *     All other failures will be considered fatal and cause the Gradle build to fail.
 *     Fatal failures could include failing to read or write cache entries due to file permissions, authentication or corruption errors.
 * </p>
 * <p>
 *     Every build cache implementation should define a {@link org.gradle.caching.configuration.BuildCache} configuration and {@link BuildCacheServiceFactory} factory.
 * </p>
 *
 * @since 3.5
 */
@Incubating
public interface BuildCacheService extends Closeable {
    /**
     * Load the cached entry corresponding to the given cache key. The {@code reader} will be called if an entry is found in the cache.
     *
     * @param key the cache key.
     * @param reader the reader to read the data corresponding to the cache key.
     * @return {@code true} if an entry was found, {@code false} otherwise.
     * @throws BuildCacheException if the cache fails to load a cache entry for the given key
     */
    boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException;

    /**
     * Store the cache entry with the given cache key. The {@code writer} will be called to actually write the data.
     *
     * @param key the cache key.
     * @param writer the writer to write the data corresponding to the cache key.
     * @throws BuildCacheException if the cache fails to store a cache entry for the given key
     */
    void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException;

    /**
     * Clean up any resources held by the cache once it's not used anymore.
     *
     * @throws IOException if the cache fails to close cleanly.
     */
    @Override
    void close() throws IOException;
}
