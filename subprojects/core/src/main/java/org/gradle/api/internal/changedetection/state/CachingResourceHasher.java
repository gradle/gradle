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

import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Caches the result of hashing regular files with a {@link ResourceHasher}.
 * It does not cache the result of hashing {@link ZipEntry}s.
 * It also caches the absence of a hash.
 */
public class CachingResourceHasher implements ResourceHasher {
    private final ResourceHasher delegate;
    private final ResourceSnapshotterCacheService resourceSnapshotterCacheService;
    private final HashCode delegateConfigurationHash;

    public CachingResourceHasher(ResourceHasher delegate, ResourceSnapshotterCacheService resourceSnapshotterCacheService) {
        this.delegate = delegate;
        this.resourceSnapshotterCacheService = resourceSnapshotterCacheService;
        Hasher hasher = Hashing.newHasher();
        delegate.appendConfigurationToHasher(hasher);
        this.delegateConfigurationHash = hasher.hash();
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        return resourceSnapshotterCacheService.hashFile(fileSnapshot, delegate, delegateConfigurationHash);
    }

    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        return delegate.hash(zipEntryContext);
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
    }
}
