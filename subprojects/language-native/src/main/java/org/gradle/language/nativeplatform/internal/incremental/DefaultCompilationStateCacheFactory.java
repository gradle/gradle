/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.ObjectHolder;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

@ServiceScope(Scopes.Gradle.class)
public class DefaultCompilationStateCacheFactory implements CompilationStateCacheFactory, Closeable {

    private final IndexedCache<String, CompilationState> compilationStateIndexedCache;
    private final PersistentCache cache;

    public DefaultCompilationStateCacheFactory(BuildScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        cache = cacheBuilderFactory
                .createCacheBuilder("nativeCompile")
                .withDisplayName("native compile cache")
                .withLockOptions(mode(FileLockManager.LockMode.OnDemand)) // Lock on demand
                .open();
        IndexedCacheParameters<String, CompilationState> parameters = IndexedCacheParameters.of("nativeCompile", String.class, new CompilationStateSerializer())
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false));

        compilationStateIndexedCache = cache.createIndexedCache(parameters);
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public ObjectHolder<CompilationState> create(String taskPath) {
        return new SimplePersistentObjectHolder(taskPath, compilationStateIndexedCache);
    }

    private static class SimplePersistentObjectHolder implements ObjectHolder<CompilationState> {
        private final String taskPath;
        private final IndexedCache<String, CompilationState> compilationStateIndexedCache;

        SimplePersistentObjectHolder(String taskPath, IndexedCache<String, CompilationState> compilationStateIndexedCache) {
            this.taskPath = taskPath;
            this.compilationStateIndexedCache = compilationStateIndexedCache;
        }

        @Override
        public CompilationState get() {
            return compilationStateIndexedCache.getIfPresent(taskPath);
        }

        @Override
        public void set(CompilationState newValue) {
            compilationStateIndexedCache.put(taskPath, newValue);
        }

        @Override
        public CompilationState update(UpdateAction<CompilationState> updateAction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompilationState maybeUpdate(UpdateAction<CompilationState> updateAction) {
            throw new UnsupportedOperationException();
        }
    }
}
