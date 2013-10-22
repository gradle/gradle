/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.language.c.internal.incremental;

import org.gradle.CacheUsage;
import org.gradle.api.internal.changedetection.state.Hasher;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.PersistentIndexedCacheParameters;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class IncrementalCompileProcessorFactory {
    private final RegexBackedIncludesParser includesParser = new RegexBackedIncludesParser();
    private final CacheFactory cacheFactory;
    private final Hasher hasher;

    public IncrementalCompileProcessorFactory(CacheFactory cacheFactory, Hasher hasher) {
        this.cacheFactory = cacheFactory;
        this.hasher = hasher;
    }

    public IncrementalCompileProcessor create(File cacheDir, String cacheKey, Iterable<File> includes) {
        // Cache is private to a named task
        File privateCacheDir = new File(cacheDir, cacheKey);
        PersistentCache cache = cacheFactory.open(privateCacheDir, "cppCompile", CacheUsage.ON, null, Collections.<String, Object>emptyMap(), FileLockManager.LockMode.Exclusive, null);

        PersistentIndexedCache<File, FileState> stateCache = createCache(cache, "state", File.class, new DefaultSerializer<FileState>(FileState.class.getClassLoader()));

        // TODO:DAZ This doesn't need to be an indexed cache: need PersistentCache.createStateCache()
        PersistentIndexedCache<String, List<File>> listCache = createCache(cache, "previous", String.class, new DefaultSerializer<List<File>>());

        DefaultSourceDependencyParser dependencyParser = new DefaultSourceDependencyParser(includesParser, CollectionUtils.toList(includes));
        return new IncrementalCompileProcessor(cache, stateCache, listCache, dependencyParser, hasher);
    }

    private <U, V> PersistentIndexedCache<U, V> createCache(PersistentCache cache, String name, Class<U> keyType, DefaultSerializer<V> fileStateDefaultSerializer) {
        File cacheFile = new File(cache.getBaseDir(), name + ".bin");
        return cache.createCache(new PersistentIndexedCacheParameters<U, V>(cacheFile, keyType, fileStateDefaultSerializer));
    }
}
