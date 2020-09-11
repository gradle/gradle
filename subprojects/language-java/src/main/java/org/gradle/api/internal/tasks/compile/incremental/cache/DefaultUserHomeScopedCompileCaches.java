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
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotDataSerializer;
import org.gradle.api.internal.tasks.compile.incremental.classpath.DefaultClasspathEntrySnapshotCache;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultUserHomeScopedCompileCaches implements UserHomeScopedCompileCaches, Closeable {
    private final ClasspathEntrySnapshotCache classpathEntrySnapshotCache;
    private final PersistentCache cache;

    public DefaultUserHomeScopedCompileCaches(FileSystemAccess fileSystemAccess, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, StringInterner interner) {
        cache = cacheRepository
            .cache("javaCompile")
            .withDisplayName("Java compile cache")
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand)) // Lock on demand
            .open();
        PersistentIndexedCacheParameters<HashCode, ClasspathEntrySnapshotData> jarCacheParameters = PersistentIndexedCacheParameters.of("jarAnalysis", new HashCodeSerializer(), new ClasspathEntrySnapshotDataSerializer(interner))
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(20000, true));
        this.classpathEntrySnapshotCache = new DefaultClasspathEntrySnapshotCache(fileSystemAccess, cache.createCache(jarCacheParameters));
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public ClasspathEntrySnapshotCache getClasspathEntrySnapshotCache() {
        return classpathEntrySnapshotCache;
    }
}
