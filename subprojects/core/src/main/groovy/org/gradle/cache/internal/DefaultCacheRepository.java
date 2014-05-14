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
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.LockOptions;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static org.gradle.cache.internal.FileLockManager.LockMode;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCacheRepository implements CacheRepository {
    private final CacheScopeMapping cacheScopeMapping;
    private final CacheFactory factory;

    public DefaultCacheRepository(CacheScopeMapping cacheScopeMapping, CacheFactory factory) {
        this.cacheScopeMapping = cacheScopeMapping;
        this.factory = factory;
    }

    public CacheBuilder store(String key) {
        return new PersistentStoreBuilder(null, key);
    }

    public CacheBuilder store(Object scope, String key) {
        return new PersistentStoreBuilder(scope, key);
    }

    public CacheBuilder cache(String key) {
        return new PersistentCacheBuilder(null, key);
    }

    public CacheBuilder cache(File baseDir) {
        return new PersistentCacheBuilder(baseDir);
    }

    public CacheBuilder cache(Object scope, String key) {
        return new PersistentCacheBuilder(scope, key);
    }

    private abstract class AbstractCacheBuilder implements CacheBuilder {
        final Object scope;
        final String key;
        final File baseDir;
        Map<String, ?> properties = Collections.emptyMap();
        CacheValidator validator;
        Action<? super PersistentCache> initializer;
        LockOptions lockOptions = mode(LockMode.Shared);
        String displayName;
        VersionStrategy versionStrategy = VersionStrategy.CachePerVersion;

        protected AbstractCacheBuilder(Object scope, String key) {
            this.scope = scope;
            this.key = key;
            this.baseDir = null;
        }

        protected AbstractCacheBuilder(File baseDir) {
            this.scope = null;
            this.key = null;
            this.baseDir = baseDir;
        }

        public CacheBuilder withProperties(Map<String, ?> properties) {
            this.properties = properties;
            return this;
        }

        public CacheBuilder withCrossVersionCache() {
            this.versionStrategy = VersionStrategy.SharedCache;
            return this;
        }

        public CacheBuilder withValidator(CacheValidator validator) {
            this.validator = validator;
            return this;
        }

        public CacheBuilder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public CacheBuilder withLockOptions(LockOptions lockOptions) {
            this.lockOptions = lockOptions;
            return this;
        }

        public CacheBuilder withInitializer(Action<? super PersistentCache> initializer) {
            this.initializer = initializer;
            return this;
        }

        public PersistentCache open() {
            File cacheBaseDir;
            if (baseDir != null) {
                cacheBaseDir = baseDir;
            } else {
                cacheBaseDir = cacheScopeMapping.getBaseDirectory(scope, key, versionStrategy);
            }
            return doOpen(cacheBaseDir, properties, validator);
        }

        protected abstract PersistentCache doOpen(File cacheDir, Map<String, ?> properties, CacheValidator validator);
    }

    private class PersistentCacheBuilder extends AbstractCacheBuilder {
        private PersistentCacheBuilder(Object scope, String key) {
            super(scope, key);
        }

        private PersistentCacheBuilder(File baseDir) {
            super(baseDir);
        }

        @Override
        protected PersistentCache doOpen(File cacheDir, Map<String, ?> properties, CacheValidator validator) {
            return factory.open(cacheDir, displayName, validator, properties, lockOptions, initializer);
        }
    }

    private class PersistentStoreBuilder extends AbstractCacheBuilder {
        private PersistentStoreBuilder(Object scope, String key) {
            super(scope, key);
        }

        @Override
        protected PersistentCache doOpen(File cacheDir, Map<String, ?> properties, CacheValidator validator) {
            if (!properties.isEmpty()) {
                throw new UnsupportedOperationException("Properties are not supported for stores.");
            }
            return factory.openStore(cacheDir, displayName, lockOptions, initializer);
        }
    }
}
