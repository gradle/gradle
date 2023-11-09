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

package org.gradle.api.internal.cache;

import org.gradle.cache.internal.DecompressionCache;
import org.gradle.cache.internal.DecompressionCacheFactory;
import org.gradle.cache.internal.DefaultDecompressionCache;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Implements a factory to create a {@link DecompressionCache} for a given {@link ScopedCacheBuilderFactory}.
 *
 * This class manages a singleton cache and creates it on demand. Closing this factory will close the cache.
 */
public class DefaultDecompressionCacheFactory implements DecompressionCacheFactory, Closeable {
    private final Supplier<? extends ScopedCacheBuilderFactory> cacheBuilderFactorySupplier;
    private final File rootLockDir;
    private volatile DecompressionCache cache;

    public DefaultDecompressionCacheFactory(Supplier<? extends ScopedCacheBuilderFactory> cacheBuilderFactorySupplier, File projectCacheDir) {
        this.cacheBuilderFactorySupplier = cacheBuilderFactorySupplier;
        this.rootLockDir = projectCacheDir;
    }

    @Nonnull
    @Override
    public DecompressionCache create() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = new DefaultDecompressionCache(cacheBuilderFactorySupplier.get(), rootLockDir);
                }
            }
        }
        return cache;
    }

    @Override
    public void close() throws IOException {
        if (cache != null) {
            cache.close();
        }
    }
}
