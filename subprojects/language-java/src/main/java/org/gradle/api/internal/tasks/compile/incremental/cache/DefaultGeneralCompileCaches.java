/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisSerializer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.deps.LocalClassSetAnalysisStore;
import org.gradle.api.internal.tasks.compile.incremental.jar.DefaultJarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotDataSerializer;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotDataSerializer;
import org.gradle.api.internal.tasks.compile.incremental.jar.LocalJarClasspathSnapshotStore;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultGeneralCompileCaches implements GeneralCompileCaches, Closeable {
    private final ClassAnalysisCache classAnalysisCache;
    private final JarSnapshotCache jarSnapshotCache;
    private final PersistentCache cache;
    private final PersistentIndexedCache<String, JarClasspathSnapshotData> taskJarCache;
    private final PersistentIndexedCache<String, ClassSetAnalysisData> taskCompileCache;

    public DefaultGeneralCompileCaches(CacheRepository cacheRepository, Gradle gradle, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        cache = cacheRepository
                .cache(gradle, "javaCompile")
                .withDisplayName("Java compile cache")
                .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
                .open();
        PersistentIndexedCacheParameters<HashCode, ClassAnalysis> classCacheParameters = new PersistentIndexedCacheParameters<HashCode, ClassAnalysis>("classAnalysis", new HashCodeSerializer(), new ClassAnalysisSerializer())
                .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(400000, true));
        this.classAnalysisCache = new DefaultClassAnalysisCache(cache.createCache(classCacheParameters));

        PersistentIndexedCacheParameters<HashCode, JarSnapshotData> jarCacheParameters = new PersistentIndexedCacheParameters<HashCode, JarSnapshotData>("jarAnalysis", new HashCodeSerializer(), new JarSnapshotDataSerializer())
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(20000, true));
        this.jarSnapshotCache = new DefaultJarSnapshotCache(cache.createCache(jarCacheParameters));

        PersistentIndexedCacheParameters<String, JarClasspathSnapshotData> taskJarCacheParameters = new PersistentIndexedCacheParameters<String, JarClasspathSnapshotData>("taskJars", String.class, new JarClasspathSnapshotDataSerializer())
                .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false));
        taskJarCache = cache.createCache(taskJarCacheParameters);

        PersistentIndexedCacheParameters<String, ClassSetAnalysisData> taskCompileCacheParameters = new PersistentIndexedCacheParameters<String, ClassSetAnalysisData>("taskHistory", String.class, new ClassSetAnalysisData.Serializer())
                .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false));
        taskCompileCache = cache.createCache(taskCompileCacheParameters);
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public ClassAnalysisCache getClassAnalysisCache() {
        return classAnalysisCache;
    }

    @Override
    public JarSnapshotCache getJarSnapshotCache() {
        return jarSnapshotCache;
    }

    @Override
    public LocalJarClasspathSnapshotStore createLocalJarClasspathSnapshotStore(String taskPath) {
        return new LocalJarClasspathSnapshotStore(taskPath, taskJarCache);
    }

    @Override
    public LocalClassSetAnalysisStore createLocalClassSetAnalysisStore(String taskPath) {
        return new LocalClassSetAnalysisStore(taskPath, taskCompileCache);
    }
}
