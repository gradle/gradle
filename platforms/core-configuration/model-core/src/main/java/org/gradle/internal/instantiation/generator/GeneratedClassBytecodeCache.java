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

package org.gradle.internal.instantiation.generator;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Abstraction for caching generated decorated class bytecode to disk.
 * <p>
 * Implementations use {@code CacheBasedImmutableWorkspaceProvider} for workspace management,
 * thread safety, and cache cleanup.
 */
public interface GeneratedClassBytecodeCache {

    /**
     * Try to load cached bytecode for the given cache key.
     *
     * @return the cached data, or null if not cached
     */
    @Nullable
    CachedClassData load(String cacheKey);

    /**
     * Store bytecode and metadata for the given cache key.
     */
    void store(String cacheKey, CachedClassData data);

    /**
     * Metadata about a generated class that is persisted alongside the bytecode.
     */
    class CachedClassData {
        private final byte[] bytecode;
        private final String generatedClassName;
        private final List<String> injectedServiceClassNames;
        private final List<String> annotationClassNames;
        private final boolean managed;
        private final int factoryId;

        public CachedClassData(
            byte[] bytecode,
            String generatedClassName,
            List<String> injectedServiceClassNames,
            List<String> annotationClassNames,
            boolean managed,
            int factoryId
        ) {
            this.bytecode = bytecode;
            this.generatedClassName = generatedClassName;
            this.injectedServiceClassNames = injectedServiceClassNames;
            this.annotationClassNames = annotationClassNames;
            this.managed = managed;
            this.factoryId = factoryId;
        }

        public byte[] getBytecode() {
            return bytecode;
        }

        public String getGeneratedClassName() {
            return generatedClassName;
        }

        public List<String> getInjectedServiceClassNames() {
            return injectedServiceClassNames;
        }

        public List<String> getAnnotationClassNames() {
            return annotationClassNames;
        }

        public boolean isManaged() {
            return managed;
        }

        public int getFactoryId() {
            return factoryId;
        }
    }

    /**
     * A no-op implementation for when disk caching is not available.
     */
    GeneratedClassBytecodeCache NONE = new GeneratedClassBytecodeCache() {
        @Override
        @Nullable
        public CachedClassData load(String cacheKey) {
            return null;
        }

        @Override
        public void store(String cacheKey, CachedClassData data) {
        }
    };
}
