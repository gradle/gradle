/*
 * Copyright 2021 the original author or authors.
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

public abstract class AbstractScopedCache implements ScopedCache {
    private final CacheScopeMapping cacheScopeMapping;
    private final CacheRepository cacheRepository;
    private final File rootDir;

    public AbstractScopedCache(File rootDir, CacheRepository cacheRepository) {
        this.rootDir = rootDir;
        this.cacheScopeMapping = new DefaultCacheScopeMapping(rootDir, GradleVersion.current());
        this.cacheRepository = cacheRepository;
    }

    protected CacheRepository getCacheRepository() {
        return cacheRepository;
    }

    @Override
    public File getRootDir() {
        return rootDir;
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
        return cacheScopeMapping.getBaseDirectory(rootDir, key, VersionStrategy.CachePerVersion);
    }

    @Override
    public File baseDirForCrossVersionCache(String key) {
        return cacheScopeMapping.getBaseDirectory(rootDir, key, VersionStrategy.SharedCache);
    }
}
