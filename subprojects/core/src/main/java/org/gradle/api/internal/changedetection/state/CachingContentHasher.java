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

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

/**
 * Caches the result of hashing a {@link RegularFileSnapshot} with a {@link ResourceHasher}.
 * It does not cache the result of hashing {@link ZipEntry}s.
 * It also caches the absence of a hash.
 */
public class CachingContentHasher implements ResourceHasher {
    private static final HashCode NO_HASH = Hashing.md5().hashString(CachingContentHasher.class.getName() + " : no hash", Charsets.UTF_8);
    private final ResourceHasher delegate;
    private final PersistentIndexedCache<HashCode, HashCode> persistentCache;
    private final byte[] delegateImplementationHash;

    public CachingContentHasher(ResourceHasher delegate, PersistentIndexedCache<HashCode, HashCode> persistentCache) {
        this.delegate = delegate;
        this.persistentCache = persistentCache;
        BuildCacheHasher hasher = new DefaultBuildCacheHasher();
        delegate.appendImplementationToHasher(hasher);
        this.delegateImplementationHash = hasher.hash().asBytes();
    }

    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        HashCode cacheKey = cacheKey(fileSnapshot);

        HashCode hash = persistentCache.get(cacheKey);
        if (hash != null) {
            if (hash.equals(NO_HASH)) {
                return null;
            }
            return hash;
        }

        hash = delegate.hash(fileSnapshot);

        if (hash != null) {
            persistentCache.put(cacheKey, hash);
        } else {
            persistentCache.put(cacheKey, NO_HASH);
        }
        return hash;
    }

    private HashCode cacheKey(RegularFileSnapshot fileSnapshot) {
        BuildCacheHasher hasher = new DefaultBuildCacheHasher();
        hasher.putBytes(delegateImplementationHash);
        hasher.putBytes(fileSnapshot.getContent().getContentMd5().asBytes());
        return hasher.hash();
    }

    @Override
    public HashCode hash(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        return delegate.hash(zipEntry, zipInput);
    }

    @Override
    public void appendImplementationToHasher(BuildCacheHasher hasher) {
        delegate.appendImplementationToHasher(hasher);
    }
}
