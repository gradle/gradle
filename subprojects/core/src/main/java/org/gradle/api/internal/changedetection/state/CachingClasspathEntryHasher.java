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

import com.google.common.hash.HashCode;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CachingClasspathEntryHasher implements ClasspathEntryHasher {
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR = new Comparator<FileDetails>() {
        @Override
        public int compare(FileDetails o1, FileDetails o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };
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
            return signature;
        }

        signature = delegate.hash(fileDetails);

        if (signature!=null) {
            persistentCache.put(contentMd5, signature);
        }
        return signature;
    }

    @Override
    public List<FileDetails> hashDir(List<FileDetails> fileDetails) {
        // Collect the signatures of each class file
        List<FileDetails> sorted = new ArrayList<FileDetails>(fileDetails.size());
        for (FileDetails details : fileDetails) {
            if (details.getType() == FileType.RegularFile) {
                HashCode signatureForClass = hash(details);
                if (signatureForClass == null) {
                    // Should be excluded
                    continue;
                }
                sorted.add(details.withContentHash(signatureForClass));
            }
        }

        // Sort as their order is not important
        Collections.sort(sorted, FILE_DETAILS_COMPARATOR);
        return sorted;
    }
}
