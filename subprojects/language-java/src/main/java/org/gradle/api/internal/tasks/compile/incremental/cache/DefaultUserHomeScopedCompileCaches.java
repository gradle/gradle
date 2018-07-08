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

import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.tasks.compile.incremental.jar.DefaultJarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotDataSerializer;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultUserHomeScopedCompileCaches implements UserHomeScopedCompileCaches, Closeable {
    private final JarSnapshotCache jarSnapshotCache;
    private final PersistentCache cache;

    public DefaultUserHomeScopedCompileCaches(FileHasher fileHasher, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        cache = cacheRepository
            .cache("javaCompile")
            .withDisplayName("Java compile cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
            .open();
        PersistentIndexedCacheParameters<HashCode, JarSnapshotData> jarCacheParameters = new PersistentIndexedCacheParameters<HashCode, JarSnapshotData>("jarAnalysis", new HashCodeSerializer(), new JarSnapshotDataSerializer())
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(20000, true));
        this.jarSnapshotCache = new DefaultJarSnapshotCache(fileHasher, cache.createCache(jarCacheParameters));
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public JarSnapshotCache getJarSnapshotCache() {
        return jarSnapshotCache;
    }
}
