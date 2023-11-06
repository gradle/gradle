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
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Map;

/**
 * Static util class for obtaining test doubles for a {@link DecompressionCache}.
 */
public abstract class TestCaches {
    private TestCaches() {}

    public static DecompressionCache decompressionCache(File cacheDir) {
        return decompressionCacheFactory(cacheDir).create();
    }

    public static DecompressionCacheFactory decompressionCacheFactory(File cacheDir) {
        return new DecompressionCacheFactory() {
            final CacheBuilder cacheBuilder = new CacheBuilder() {
                final TestInMemoryCacheFactory cacheFactory = new TestInMemoryCacheFactory();

                @Override
                public CacheBuilder withProperties(Map<String, ?> properties) {
                    return this;
                }

                @Override
                public CacheBuilder withCrossVersionCache(LockTarget lockTarget) {
                    return this;
                }

                @Override
                public CacheBuilder withDisplayName(String displayName) {
                    return this;
                }

                @Override
                public CacheBuilder withLockOptions(LockOptions lockOptions) {
                    return this;
                }

                @Override
                public CacheBuilder withInitializer(Action<? super PersistentCache> initializer) {
                    return this;
                }

                @Override
                public CacheBuilder withCleanupStrategy(CacheCleanupStrategy cleanup) {
                    return this;
                }

                @Override
                public PersistentCache open() throws CacheOpenException {
                    return cacheFactory.open(cacheDir, "test compression cache");
                }
            };

            @Nonnull
            @Override
            public DecompressionCache create() {
                return new DefaultDecompressionCache(cacheBuilder);
            }
        };
    }
}
