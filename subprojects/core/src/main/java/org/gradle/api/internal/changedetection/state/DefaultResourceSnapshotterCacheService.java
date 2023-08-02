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

import org.gradle.cache.IndexedCache;
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContextHasher;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.io.IoSupplier;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import javax.annotation.Nullable;
import java.io.IOException;

public class DefaultResourceSnapshotterCacheService implements ResourceSnapshotterCacheService {
    private static final HashCode NO_HASH = Hashing.signature(CachingResourceHasher.class.getName() + " : no hash");
    private final IndexedCache<HashCode, HashCode> indexedCache;

    public DefaultResourceSnapshotterCacheService(IndexedCache<HashCode, HashCode> indexedCache) {
        this.indexedCache = indexedCache;
    }

    @Nullable
    @Override
    public HashCode hashFile(FileSystemLocationSnapshot snapshot, FileSystemLocationSnapshotHasher hasher, HashCode configurationHash) throws IOException {
        return hashFile(snapshot, () -> hasher.hash(snapshot), configurationHash);
    }

    @Nullable
    @Override
    public HashCode hashFile(RegularFileSnapshotContext fileSnapshotContext, RegularFileSnapshotContextHasher hasher, HashCode configurationHash) throws IOException {
        return hashFile(fileSnapshotContext.getSnapshot(), () -> hasher.hash(fileSnapshotContext), configurationHash);
    }

    @Nullable
    private HashCode hashFile(FileSystemLocationSnapshot snapshot, IoSupplier<HashCode> hashCodeSupplier, HashCode configurationHash) throws IOException {
        HashCode resourceHashCacheKey = resourceHashCacheKey(snapshot.getHash(), configurationHash);

        HashCode resourceHash = indexedCache.getIfPresent(resourceHashCacheKey);
        if (resourceHash != null) {
            if (resourceHash.equals(NO_HASH)) {
                return null;
            }
            return resourceHash;
        }

        resourceHash = hashCodeSupplier.get();

        if (resourceHash != null) {
            indexedCache.put(resourceHashCacheKey, resourceHash);
        } else {
            indexedCache.put(resourceHashCacheKey, NO_HASH);
        }
        return resourceHash;
    }

    private static HashCode resourceHashCacheKey(HashCode contentHash, HashCode configurationHash) {
        Hasher hasher = Hashing.newHasher();
        hasher.putHash(configurationHash);
        hasher.putHash(contentHash);
        return hasher.hash();
    }
}
