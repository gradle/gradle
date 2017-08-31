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

import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.PersistentStateCache;

import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCompilationStateCacheFactory implements CompilationStateCacheFactory, Closeable {

    private final PersistentIndexedCache<String, CompilationState> compilationStateIndexedCache;
    private final PersistentCache cache;

    public DefaultCompilationStateCacheFactory(CacheRepository cacheRepository, Gradle gradle, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        cache = cacheRepository
                .cache(gradle, "nativeCompile")
                .withDisplayName("native compile cache")
                .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
                .open();
        PersistentIndexedCacheParameters<String, CompilationState> parameters = new PersistentIndexedCacheParameters<String, CompilationState>("nativeCompile", String.class, new CompilationStateSerializer())
                .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false));

        compilationStateIndexedCache = cache.createCache(parameters);
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public PersistentStateCache<CompilationState> create(String taskPath) {
        return new PersistentCompilationStateCache(taskPath, compilationStateIndexedCache);
    }

    private static class PersistentCompilationStateCache implements PersistentStateCache<CompilationState> {
        private final String taskPath;
        private final PersistentIndexedCache<String, CompilationState> compilationStateIndexedCache;

        PersistentCompilationStateCache(String taskPath, PersistentIndexedCache<String, CompilationState> compilationStateIndexedCache) {
            this.taskPath = taskPath;
            this.compilationStateIndexedCache = compilationStateIndexedCache;
        }

        @Override
        public CompilationState get() {
            return compilationStateIndexedCache.get(taskPath);
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
