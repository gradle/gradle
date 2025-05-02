/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.DefaultLockOptions;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode;

public class DefaultCacheBuilder implements CacheBuilder {
    private final CacheFactory factory;
    private final File baseDir;
    private Map<String, ?> properties = Collections.emptyMap();
    private Consumer<? super PersistentCache> initializer;
    private CacheCleanupStrategy cacheCleanupStrategy = CacheCleanupStrategy.NO_CLEANUP;
    private LockOptions lockOptions = mode(FileLockManager.LockMode.Shared);
    private String displayName;

    public DefaultCacheBuilder(CacheFactory factory, File baseDir) {
        this.factory = factory;
        this.baseDir = baseDir;
    }

    @Override
    public CacheBuilder withProperties(Map<String, ?> properties) {
        this.properties = properties;
        return this;
    }

    @Override
    public CacheBuilder withDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public CacheBuilder withInitialLockMode(FileLockManager.LockMode mode) {
        this.lockOptions = DefaultLockOptions.mode(mode);
        return this;
    }

    @Override
    public CacheBuilder withInitializer(Consumer<? super PersistentCache> initializer) {
        this.initializer = initializer;
        return this;
    }

    @Override
    public CacheBuilder withCleanupStrategy(CacheCleanupStrategy cacheCleanupStrategy) {
        this.cacheCleanupStrategy = cacheCleanupStrategy;
        return this;
    }

    @Override
    public PersistentCache open() {
        return factory.open(baseDir, displayName, properties, lockOptions, initializer, cacheCleanupStrategy);
    }
}
