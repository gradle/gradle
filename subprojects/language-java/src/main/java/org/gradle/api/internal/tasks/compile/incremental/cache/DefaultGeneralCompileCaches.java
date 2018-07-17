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

import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.WellKnownFileLocations;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisSerializer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.deps.LocalClassSetAnalysisStore;
import org.gradle.api.internal.tasks.compile.incremental.classpath.DefaultClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotDataSerializer;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotDataSerializer;
import org.gradle.api.internal.tasks.compile.incremental.classpath.LocalClasspathSnapshotStore;
import org.gradle.api.internal.tasks.compile.incremental.classpath.SplitClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorPathStore;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;

import java.io.Closeable;
import java.io.File;
import java.util.List;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultGeneralCompileCaches implements GeneralCompileCaches, Closeable {
    private final ClassAnalysisCache classAnalysisCache;
    private final ClasspathEntrySnapshotCache classpathEntrySnapshotCache;
    private final PersistentCache cache;
    private final PersistentIndexedCache<String, ClasspathSnapshotData> taskJarCache;
    private final PersistentIndexedCache<String, ClassSetAnalysisData> taskCompileCache;
    private final PersistentIndexedCache<String, List<File>> taskProcessorPathCache;

    public DefaultGeneralCompileCaches(FileSystemSnapshotter fileSystemSnapshotter, UserHomeScopedCompileCaches userHomeScopedCompileCaches, CacheRepository cacheRepository, Gradle gradle, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, WellKnownFileLocations fileLocations) {
        cache = cacheRepository
            .cache(gradle, "javaCompile")
            .withDisplayName("Java compile cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
            .open();
        PersistentIndexedCacheParameters<HashCode, ClassAnalysis> classCacheParameters = new PersistentIndexedCacheParameters<HashCode, ClassAnalysis>("classAnalysis", new HashCodeSerializer(), new ClassAnalysisSerializer())
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(400000, true));
        this.classAnalysisCache = new DefaultClassAnalysisCache(cache.createCache(classCacheParameters));

        PersistentIndexedCacheParameters<HashCode, ClasspathEntrySnapshotData> jarCacheParameters = new PersistentIndexedCacheParameters<HashCode, ClasspathEntrySnapshotData>("jarAnalysis", new HashCodeSerializer(), new ClasspathEntrySnapshotDataSerializer())
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(20000, true));
        this.classpathEntrySnapshotCache = new SplitClasspathEntrySnapshotCache(fileLocations, userHomeScopedCompileCaches.getClasspathEntrySnapshotCache(), new DefaultClasspathEntrySnapshotCache(fileSystemSnapshotter, cache.createCache(jarCacheParameters)));

        PersistentIndexedCacheParameters<String, ClasspathSnapshotData> taskJarCacheParameters = new PersistentIndexedCacheParameters<String, ClasspathSnapshotData>("taskJars", String.class, new ClasspathSnapshotDataSerializer())
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false));
        taskJarCache = cache.createCache(taskJarCacheParameters);

        PersistentIndexedCacheParameters<String, ClassSetAnalysisData> taskCompileCacheParameters = new PersistentIndexedCacheParameters<String, ClassSetAnalysisData>("taskHistory", String.class, new ClassSetAnalysisData.Serializer())
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false));
        taskCompileCache = cache.createCache(taskCompileCacheParameters);
        PersistentIndexedCacheParameters<String, List<File>> taskProcessorPathCacheParameters = new PersistentIndexedCacheParameters<String, List<File>>("processorPath", String.class, new ListSerializer<File>(BaseSerializerFactory.FILE_SERIALIZER))
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false));
        taskProcessorPathCache = cache.createCache(taskProcessorPathCacheParameters);
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
    public ClasspathEntrySnapshotCache getClasspathEntrySnapshotCache() {
        return classpathEntrySnapshotCache;
    }

    @Override
    public LocalClasspathSnapshotStore createLocalClasspathSnapshotStore(String taskPath) {
        return new LocalClasspathSnapshotStore(taskPath, taskJarCache);
    }

    @Override
    public LocalClassSetAnalysisStore createLocalClassSetAnalysisStore(String taskPath) {
        return new LocalClassSetAnalysisStore(taskPath, taskCompileCache);
    }

    @Override
    public AnnotationProcessorPathStore createAnnotationProcessorPathStore(String taskpath) {
        return new AnnotationProcessorPathStore(taskpath, taskProcessorPathCache);
    }
}
