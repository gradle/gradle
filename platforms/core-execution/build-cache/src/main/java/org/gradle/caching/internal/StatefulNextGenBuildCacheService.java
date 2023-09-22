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

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.gradle.cache.HasCleanupAction;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Left to keep H2BuildCacheService logic
 */
public interface StatefulNextGenBuildCacheService extends BuildCacheService, HasCleanupAction, Closeable {
    /**
     * Returns whether the given entry exists in the cache.
     *
     * @param key the cache key.
     * @return {code true} if the entry exists in the cache.
     */
    boolean contains(BuildCacheKey key);

    @Override
    default void store(BuildCacheKey key, BuildCacheEntryWriter legacyWriter) throws BuildCacheException {
        NextGenWriter writer;
        if (legacyWriter instanceof NextGenWriter) {
            writer = (NextGenWriter) legacyWriter;
        } else {
            writer = new NextGenWriter() {
                @Override
                public InputStream openStream() throws IOException {
                    UnsynchronizedByteArrayOutputStream data = new UnsynchronizedByteArrayOutputStream();
                    writeTo(data);
                    return data.toInputStream();
                }

                @Override
                public void writeTo(OutputStream output) throws IOException {
                    legacyWriter.writeTo(output);
                }

                @Override
                public long getSize() {
                    return legacyWriter.getSize();
                }
            };
        }
        store(key, writer);
    }

    void store(BuildCacheKey key, NextGenWriter writer) throws BuildCacheException;

    void open();

    @Override
    void close();

    /**
     * A {@link BuildCacheEntryWriter} that can open an {@link InputStream} to the data instead of writing it to an {@link OutputStream}.
     *
     * In some backend implementations this results in better performance.
     */
    interface NextGenWriter extends BuildCacheEntryWriter {
        InputStream openStream() throws IOException;
    }
}
