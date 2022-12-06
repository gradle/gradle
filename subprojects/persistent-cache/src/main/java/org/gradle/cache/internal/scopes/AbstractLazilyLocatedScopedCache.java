/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.cache.internal.scopes;

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.cache.scopes.ScopedCache;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.function.Supplier;

/**
 * Abstract implementation of {@link ScopedCache}, similar to {@link AbstractScopedCache} but performs
 * lazy evaluation of the root directory, so that user has time to configure the location of the project's
 * build directory before the cache is created there.
 */
public abstract class AbstractLazilyLocatedScopedCache implements ScopedCache {
    private final Supplier<File> rootDirSupplier;
    private File rootDir;
    private final CacheRepository cacheRepository;
    private CacheScopeMapping cacheScopeMapping;

    public AbstractLazilyLocatedScopedCache(Supplier<File> rootDirSupplier, CacheRepository cacheRepository) {
        this.rootDirSupplier = rootDirSupplier;
        this.cacheRepository = cacheRepository;
    }

    private File createOrRetrieveRootDir() {
        if (null == rootDir) {
            this.rootDir = rootDirSupplier.get();
        }
        return rootDir;
    }

    private CacheScopeMapping createOrRetrieveCacheScopeMapping() {
        if (null == cacheScopeMapping) {
           this.cacheScopeMapping = new DefaultCacheScopeMapping(createOrRetrieveRootDir(), GradleVersion.current());
        }
        return cacheScopeMapping;
    }

    @Override
    public File getRootDir() {
        return createOrRetrieveRootDir();
    }

    @Override
    public CacheBuilder cache(String key) {
        return cacheRepository.cache(baseDirForCache(key));
    }

    @Override
    public CacheBuilder crossVersionCache(String key) {
        return cacheRepository.cache(baseDirForCrossVersionCache(key));
    }

    @Override
    public File baseDirForCache(String key) {
        return createOrRetrieveCacheScopeMapping().getBaseDirectory(createOrRetrieveRootDir(), key, VersionStrategy.CachePerVersion);
    }

    @Override
    public File baseDirForCrossVersionCache(String key) {
        return createOrRetrieveCacheScopeMapping().getBaseDirectory(createOrRetrieveRootDir(), key, VersionStrategy.SharedCache);
    }
}
