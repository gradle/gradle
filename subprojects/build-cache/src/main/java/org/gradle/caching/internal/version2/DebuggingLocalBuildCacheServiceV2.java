/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.caching.internal.version2;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.hash.HashCode;

import java.io.File;

public class DebuggingLocalBuildCacheServiceV2 implements LocalBuildCacheServiceV2 {
    private static final Logger LOGGER = Logging.getLogger(DebuggingLocalBuildCacheServiceV2.class);
    private final LocalBuildCacheServiceV2 delegate;

    public DebuggingLocalBuildCacheServiceV2(LocalBuildCacheServiceV2 delegate) {
        this.delegate = delegate;
    }

    @Override
    public CacheEntry get(HashCode key) {
        CacheEntry entry = delegate.get(key);
        LOGGER.lifecycle("Getting entry {}, found: {}", key, entry);
        return entry;
    }

    @Override
    public FileEntry put(HashCode key, File file) {
        LOGGER.lifecycle("Putting file {}: {}", key, file);
        return delegate.put(key, file);
    }

    @Override
    public ManifestEntry put(HashCode key, ImmutableSortedMap<String, HashCode> entries) {
        LOGGER.lifecycle("Putting manifest {}: {}", key, entries);
        return delegate.put(key, entries);
    }

    @Override
    public ResultEntry put(HashCode key, ImmutableSortedMap<String, HashCode> outputs, byte[] originMetadata) {
        LOGGER.lifecycle("Putting results {}: {}", key, outputs);
        return delegate.put(key, outputs, originMetadata);
    }
}
