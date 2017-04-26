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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

/**
 * Caches the result of hashing a {@link RegularFileSnapshot} with a {@link ContentHasher}.
 * It does not cache the result of hashing {@link ZipEntry}s.
 * It also caches the absence of a hash.
 */
public class CachingContentHasher implements ContentHasher {
    private static final HashCode NO_HASH = Hashing.md5().hashString(CachingContentHasher.class.getName() + " : no hash", Charsets.UTF_8);
    private final ContentHasher delegate;
    private final PersistentIndexedCache<HashCode, HashCode> persistentCache;

    public CachingContentHasher(ContentHasher delegate, PersistentIndexedCache<HashCode, HashCode> persistentCache) {
        this.delegate = delegate;
        this.persistentCache = persistentCache;
    }

    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        HashCode contentMd5 = fileSnapshot.getContent().getContentMd5();

        HashCode hash = persistentCache.get(contentMd5);
        if (hash != null) {
            if (hash.equals(NO_HASH)) {
                return null;
            }
            return hash;
        }

        hash = delegate.hash(fileSnapshot);

        if (hash != null) {
            persistentCache.put(contentMd5, hash);
        } else {
            persistentCache.put(contentMd5, NO_HASH);
        }
        return hash;
    }

    @Override
    public HashCode hash(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        return delegate.hash(zipEntry, zipInput);
    }
}
