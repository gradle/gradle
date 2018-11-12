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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

/**
 * A lightweight key for in-memory caching of transformation results.
 * Computing the hash key for the persistent cache is a rather expensive
 * operation, so we only calculate it when we have a cache miss in memory.
 */
class TransformationCacheKey {
    private final String absolutePath;
    private final HashCode fileContentHash;
    private final HashCode secondaryInputHash;

    public TransformationCacheKey(HashCode secondaryInputHash, String absolutePath, HashCode fileContentHash) {
        this.absolutePath = absolutePath;
        this.fileContentHash = fileContentHash;
        this.secondaryInputHash = secondaryInputHash;
    }

    public HashCode getPersistentCacheKey() {
        Hasher hasher = Hashing.newHasher();
        hasher.putHash(secondaryInputHash);
        hasher.putString(absolutePath);
        hasher.putHash(fileContentHash);
        return hasher.hash();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TransformationCacheKey cacheKey = (TransformationCacheKey) o;

        if (!fileContentHash.equals(cacheKey.fileContentHash)) {
            return false;
        }
        if (!secondaryInputHash.equals(cacheKey.secondaryInputHash)) {
            return false;
        }
        return absolutePath.equals(cacheKey.absolutePath);
    }

    @Override
    public int hashCode() {
        int result = fileContentHash.hashCode();
        result = 31 * result + absolutePath.hashCode();
        result = 31 * result + secondaryInputHash.hashCode();
        return result;
    }
}
