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

package org.gradle.api.internal.changedetection.state;

import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

/**
 * Caches the result of hashing a {@link RegularFileSnapshot} with a {@link ResourceHasher}.
 * It does not cache the result of hashing {@link ZipEntry}s.
 * It also caches the absence of a hash.
 */
public class CachingResourceHasher implements ResourceHasher {
    private final ResourceHasher delegate;
    private final ResourceSnapshotterCacheService resourceSnapshotterCacheService;
    private final byte[] delegateConfigurationHash;

    public CachingResourceHasher(ResourceHasher delegate, ResourceSnapshotterCacheService resourceSnapshotterCacheService) {
        this.delegate = delegate;
        this.resourceSnapshotterCacheService = resourceSnapshotterCacheService;
        BuildCacheHasher hasher = new DefaultBuildCacheHasher();
        delegate.appendConfigurationToHasher(hasher);
        this.delegateConfigurationHash = hasher.hash().toByteArray();
    }

    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        return resourceSnapshotterCacheService.hashFile(fileSnapshot, delegate, delegateConfigurationHash);
    }

    @Override
    public HashCode hash(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        return delegate.hash(zipEntry, zipInput);
    }

    @Override
    public void appendConfigurationToHasher(BuildCacheHasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
    }
}
