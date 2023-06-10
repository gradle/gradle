/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.internal;

import org.gradle.api.Incubating;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Build cache service with additional features for next-generation build cache implementation.
 */
@Incubating
public interface NextGenBuildCacheService extends Closeable {
    /**
     * Returns whether the given entry exists in the cache.
     *
     * @param key the cache key.
     * @return {code true} if the entry exists in the cache.
     */
    boolean contains(BuildCacheKey key);

    /**
     * Load the cached entry corresponding to the given cache key. The {@code reader} will be called if an entry is found in the cache.
     *
     * @param key the cache key.
     * @param reader the reader to read the data corresponding to the cache key.
     * @return {@code true} if an entry was found, {@code false} otherwise.
     * @throws BuildCacheException if the cache fails to load a cache entry for the given key
     */
    boolean load(BuildCacheKey key, EntryReader reader) throws BuildCacheException;

    void store(BuildCacheKey key, EntryWriter writer) throws BuildCacheException;

    interface EntryReader {
        void readFrom(InputStream input) throws IOException;
    }

    /**
     * A {@link BuildCacheEntryWriter} that can open an {@link InputStream} to the data instead of writing it to an {@link OutputStream}.
     *
     * In some backend implementations this results in better performance.
     */
    interface EntryWriter {
        InputStream openStream() throws IOException;

        long getSize();
    }

    @Override
    void close() throws IOException;
}
