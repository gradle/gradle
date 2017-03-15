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

public class CachingClasspathEntryHasher implements ClasspathEntryHasher {
    private static final HashCode NO_SIGNATURE = Hashing.md5().hashString(CachingClasspathEntryHasher.class.getName() + " : no signature", Charsets.UTF_8);
    private final ClasspathEntryHasher delegate;
    private final PersistentIndexedCache<HashCode, HashCode> persistentCache;

    public CachingClasspathEntryHasher(ClasspathEntryHasher delegate, PersistentIndexedCache<HashCode, HashCode> persistentCache) {
        this.delegate = delegate;
        this.persistentCache = persistentCache;
    }

    @Override
    public HashCode hash(FileDetails fileDetails) {
        HashCode contentMd5 = fileDetails.getContent().getContentMd5();

        HashCode signature = persistentCache.get(contentMd5);
        if (signature != null) {
            if (signature.equals(NO_SIGNATURE)) {
                return null;
            }
            return signature;
        }

        signature = delegate.hash(fileDetails);

        if (signature!=null) {
            persistentCache.put(contentMd5, signature);
        } else {
            persistentCache.put(contentMd5, NO_SIGNATURE);
        }
        return signature;
    }
}
