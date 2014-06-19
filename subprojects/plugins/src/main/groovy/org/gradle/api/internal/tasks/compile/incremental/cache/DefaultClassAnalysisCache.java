/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.cache;

import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysis;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultClassAnalysisCache implements ClassAnalysisCache {

    private final PersistentCache cacheAccess;
    private final PersistentIndexedCache<byte[], ClassAnalysis> cache;

    //TODO SF document the caches, make them consistent, and create a facade for them
    public DefaultClassAnalysisCache(CacheRepository cacheRepository) {
        cacheAccess = cacheRepository
                .cache("classAnalysisCache")
                .withDisplayName("class analysis cache")
                .withLockOptions(mode(FileLockManager.LockMode.None))
                .open();

        PersistentIndexedCacheParameters<byte[], ClassAnalysis> params =
                new PersistentIndexedCacheParameters<byte[], ClassAnalysis>("classAnalysisCache", byte[].class, ClassAnalysis.class);
        cache = cacheAccess.createCache(params);
    }

    public ClassAnalysis get(final byte[] hash, final Factory<ClassAnalysis> factory) {
        ClassAnalysis cached = cacheAccess.useCache("Loading class analysis", new Factory<ClassAnalysis>() {
            public ClassAnalysis create() {
                return cache.get(hash);
            }
        });
        if (cached != null) { //TODO SF create abstraction or unit test
            return cached;
        }

        final ClassAnalysis analysis = factory.create();
        cacheAccess.useCache("Storing class analysis", new Runnable() {
            public void run() {
                cache.put(hash, analysis);
            }
        });
        return analysis;
    }
}