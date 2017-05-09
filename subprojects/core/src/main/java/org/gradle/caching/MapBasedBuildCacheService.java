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
import org.gradle.internal.io.StreamByteBuffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

/**
 * Simple build cache implementation that delegates to a {@link ConcurrentMap}.
 *
 * @since 3.5
 */
@Incubating
public class MapBasedBuildCacheService implements BuildCacheService {
    private final ConcurrentMap<String, byte[]> delegate;

    public MapBasedBuildCacheService(ConcurrentMap<String, byte[]> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        final byte[] bytes = delegate.get(key.getHashCode());
        if (bytes == null) {
            return false;
        }
        try {
            reader.readFrom(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new BuildCacheException("loading " + key, e);
        }
        return true;
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter output) throws BuildCacheException {
        StreamByteBuffer buffer = new StreamByteBuffer();
        try {
            output.writeTo(buffer.getOutputStream());
        } catch (IOException e) {
            throw new BuildCacheException("storing " + key, e);
        }
        delegate.put(key.getHashCode(), buffer.readAsByteArray());
    }

    @Override
    public void close() throws IOException {
        // Do nothing
    }
}
