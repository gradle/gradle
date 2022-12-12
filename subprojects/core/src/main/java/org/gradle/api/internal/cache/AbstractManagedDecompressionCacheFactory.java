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
import org.gradle.cache.scopes.ScopedCache;
import org.gradle.internal.concurrent.Stoppable;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract base class for implementing factories that can be used to create {@link DecompressionCache}s.
 *
 * This class manages the caches it creates by keeping track of them, and closing them when this factory is
 * itself closed or stopped.  This allows the caches to be closed when the owning scope containing the
 * service instance of the factory implementation is closed.
 */
/* package */ abstract class AbstractManagedDecompressionCacheFactory implements DecompressionCacheFactory, Stoppable, Closeable {
    private final Set<DefaultDecompressionCache> createdCaches = new HashSet<>();

    protected abstract ScopedCache getScopedCache();

    @Override
    public DefaultDecompressionCache create() {
        DefaultDecompressionCache cache = new DefaultDecompressionCache(getScopedCache());
        createdCaches.add(cache);
        return cache;
    }

    @Override
    public void close() throws IOException {
        createdCaches.forEach(DefaultDecompressionCache::close);
    }

    @Override
    public void stop() {
        try {
            close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
