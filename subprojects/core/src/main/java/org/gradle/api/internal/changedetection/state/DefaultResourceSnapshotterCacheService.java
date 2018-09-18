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

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.RegularFileSnapshot;

public class DefaultResourceSnapshotterCacheService implements ResourceSnapshotterCacheService {
    private static final HashCode NO_HASH = Hashing.signature(CachingResourceHasher.class.getName() + " : no hash");
    private final PersistentIndexedCache<HashCode, HashCode> persistentCache;

    public DefaultResourceSnapshotterCacheService(PersistentIndexedCache<HashCode, HashCode> persistentCache) {
        this.persistentCache = persistentCache;
    }

    @Override
    public HashCode hashFile(RegularFileSnapshot fileSnapshot, RegularFileHasher hasher, HashCode configurationHash) {
        HashCode resourceHashCacheKey = resourceHashCacheKey(fileSnapshot.getHash(), configurationHash);

        HashCode resourceHash = persistentCache.get(resourceHashCacheKey);
        if (resourceHash != null) {
            if (resourceHash.equals(NO_HASH)) {
                return null;
            }
            return resourceHash;
        }

        resourceHash = hasher.hash(fileSnapshot);

        if (resourceHash != null) {
            persistentCache.put(resourceHashCacheKey, resourceHash);
        } else {
            persistentCache.put(resourceHashCacheKey, NO_HASH);
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
