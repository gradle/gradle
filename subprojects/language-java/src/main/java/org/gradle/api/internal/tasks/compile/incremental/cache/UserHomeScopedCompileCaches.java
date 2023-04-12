/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.cache;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.serialization.HierarchicalNameSerializer;
import org.gradle.cache.Cache;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.MinimalPersistentCache;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class UserHomeScopedCompileCaches implements GeneralCompileCaches, Closeable {
    private final Cache<HashCode, ClassSetAnalysisData> classpathEntrySnapshotCache;
    private final PersistentCache cache;
    private final Cache<HashCode, ClassAnalysis> classAnalysisCache;

    public UserHomeScopedCompileCaches(GlobalScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, StringInterner interner) {
        cache = cacheBuilderFactory
            .createCacheBuilder("javaCompile")
            .withDisplayName("Java compile cache")
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand)) // Lock on demand
            .open();
        IndexedCacheParameters<HashCode, ClassSetAnalysisData> jarCacheParameters = IndexedCacheParameters.of(
            "jarAnalysis",
            new HashCodeSerializer(),
            new ClassSetAnalysisData.Serializer(() -> new HierarchicalNameSerializer(interner))
        ).withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(20000, true));
        this.classpathEntrySnapshotCache = new MinimalPersistentCache<>(cache.createIndexedCache(jarCacheParameters));

        IndexedCacheParameters<HashCode, ClassAnalysis> classCacheParameters = IndexedCacheParameters.of(
            "classAnalysis",
            new HashCodeSerializer(),
            new ClassAnalysis.Serializer(interner)
        ).withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(400000, true));
        this.classAnalysisCache = new MinimalPersistentCache<>(cache.createIndexedCache(classCacheParameters));
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public Cache<HashCode, ClassSetAnalysisData> getClassSetAnalysisCache() {
        return classpathEntrySnapshotCache;
    }

    @Override
    public Cache<HashCode, ClassAnalysis> getClassAnalysisCache() {
        return classAnalysisCache;
    }
}
