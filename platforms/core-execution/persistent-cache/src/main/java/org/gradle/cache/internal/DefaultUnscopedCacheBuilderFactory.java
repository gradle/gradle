/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.DefaultLockOptions;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode;

public class DefaultUnscopedCacheBuilderFactory implements UnscopedCacheBuilderFactory {
    private final CacheFactory factory;

    public DefaultUnscopedCacheBuilderFactory(CacheFactory factory) {
        this.factory = factory;
    }

    @Override
    public CacheBuilder cache(File baseDir) {
        return new PersistentCacheBuilder(baseDir);
    }

    private class PersistentCacheBuilder implements CacheBuilder {
        final File baseDir;
        Map<String, ?> properties = Collections.emptyMap();
        Action<? super PersistentCache> initializer;
        CacheCleanupStrategy cacheCleanupStrategy;
        LockOptions lockOptions = mode(FileLockManager.LockMode.Shared);
        String displayName;

        PersistentCacheBuilder(File baseDir) {
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
        public CacheBuilder withInitializer(Action<? super PersistentCache> initializer) {
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
}
