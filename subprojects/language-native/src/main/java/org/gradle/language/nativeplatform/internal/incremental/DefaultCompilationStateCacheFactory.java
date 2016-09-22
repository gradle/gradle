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

import org.gradle.api.internal.changedetection.state.TaskHistoryStore;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStateCache;

public class DefaultCompilationStateCacheFactory implements CompilationStateCacheFactory {

    private final PersistentIndexedCache<String, CompilationState> compilationStateIndexedCache;

    public DefaultCompilationStateCacheFactory(TaskHistoryStore cacheAccess) {
        compilationStateIndexedCache = cacheAccess.createCache("compilationState", String.class, new CompilationStateSerializer());
    }

    @Override
    public PersistentStateCache<CompilationState> create(final String taskPath) {
        return new PersistentCompilationStateCache(taskPath, compilationStateIndexedCache);
    }

    private static class PersistentCompilationStateCache implements PersistentStateCache<CompilationState> {
        private final String taskPath;
        private final PersistentIndexedCache<String, CompilationState> compilationStateIndexedCache;

        public PersistentCompilationStateCache(String taskPath, PersistentIndexedCache<String, CompilationState> compilationStateIndexedCache) {
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
        public void update(UpdateAction<CompilationState> updateAction) {
            throw new UnsupportedOperationException();
        }
    }
}
