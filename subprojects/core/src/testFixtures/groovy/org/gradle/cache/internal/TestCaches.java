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

package org.gradle.cache.internal;

import org.gradle.api.Action;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.scopes.ScopedCache;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static org.gradle.cache.FileLockManager.LockMode.Exclusive;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

/**
 * Static util class for obtaining caching-related test doubles for {@link ScopedCache}, {@link PersistentCache} and similar types.
 */
public abstract class TestCaches {
    private TestCaches() {}

    public static ScopedCache scopedCache(File cacheDir) {
        return new TestInMemoryScopedCache(cacheDir);
    }

    public static DecompressionCache decompressionCache(File cacheDir) {
        return new TestInMemoryDecompressionCache(cacheDir);
    }

    private static final class TestInMemoryDecompressionCache implements DecompressionCache {
        private final PersistentCache delegate;

        private TestInMemoryDecompressionCache(File cacheDir) {
            TestInMemoryCacheBuilder cacheBuilder = new TestInMemoryCacheBuilder(cacheDir);
            delegate = cacheBuilder.open();
        }

        @Override
        public <T> T useCache(Factory<? extends T> action) {
            return delegate.useCache(action);
        }

        @Override
        public void useCache(Runnable action) {
            delegate.useCache(action);
        }

        @Override
        public <T> T withFileLock(Factory<? extends T> action) {
            return delegate.withFileLock(action);
        }

        @Override
        public void withFileLock(Runnable action) {
            delegate.withFileLock(action);
        }

        @Override
        public File getBaseDir() {
            return delegate.getBaseDir();
        }
    }

    private static final class TestInMemoryScopedCache implements ScopedCache {
        private final File cacheDir;

        private TestInMemoryScopedCache(File cacheDir) {
            this.cacheDir = cacheDir;
        }

        @Override
        public CacheBuilder cache(String key) {
            return new TestInMemoryCacheBuilder(baseDirForCache(key));
        }

        @Override
        public CacheBuilder crossVersionCache(String key) {
            return new TestInMemoryCacheBuilder(baseDirForCrossVersionCache(key));
        }

        @Override
        public File getRootDir() {
            return cacheDir;
        }

        @Override
        public File baseDirForCache(String key) {
            return new File(cacheDir, "base");
        }

        @Override
        public File baseDirForCrossVersionCache(String key) {
            return new File(cacheDir, "cross-version-base");
        }
    }

    private static final class TestInMemoryCacheBuilder implements CacheBuilder {
        private final CacheFactory cacheFactory = new TestInMemoryCacheFactory();
        private final File cacheDir;
        private Map<String, ?> properties = Collections.emptyMap();
        private LockTarget lockTarget = LockTarget.DefaultTarget;
        private String displayName = "Test In Memory Cache";
        private LockOptions lockOptions = mode(Exclusive);
        private Action<? super PersistentCache> initializer = Actions.doNothing();
        private CleanupAction cleanup = CleanupAction.NO_OP;

        private TestInMemoryCacheBuilder(File cacheDir) {
            this.cacheDir = cacheDir;
        }

        @Override
        public CacheBuilder withProperties(Map<String, ?> properties) {
            this.properties = properties;
            return this;
        }

        @Override
        public CacheBuilder withCrossVersionCache(LockTarget lockTarget) {
            this.lockTarget = lockTarget;
            return this;
        }

        @Override
        public CacheBuilder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        @Override
        public CacheBuilder withLockOptions(LockOptions lockOptions) {
            this.lockOptions = lockOptions;
            return this;
        }

        @Override
        public CacheBuilder withInitializer(Action<? super PersistentCache> initializer) {
            this.initializer = initializer;
            return this;
        }

        @Override
        public CacheBuilder withCleanup(CleanupAction cleanup) {
            this.cleanup = cleanup;
            return this;
        }

        @Override
        public PersistentCache open() throws CacheOpenException {
            return cacheFactory.open(cacheDir, displayName, properties, lockTarget, lockOptions, initializer, cleanup);
        }
    }
}
