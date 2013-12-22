/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.language.c.internal.incremental;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.hash.CachingHasher;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.List;

abstract class AbstractIncrementalNativeCompiler implements Compiler<NativeCompileSpec> {
    private final RegexBackedIncludesParser includesParser = new RegexBackedIncludesParser();
    private final TaskInternal task;
    private final CacheRepository cacheRepository;
    private Iterable<File> includes;

    protected AbstractIncrementalNativeCompiler(TaskInternal task, Iterable<File> includes, CacheRepository cacheRepository) {
        this.task = task;
        this.includes = includes;
        this.cacheRepository = cacheRepository;
    }

    public WorkResult execute(final NativeCompileSpec spec) {
        final PersistentCache cache = openCache(task);
        try {
            return cache.useCache("incremental compile", new Factory<WorkResult>() {
                public WorkResult create() {
                    IncrementalCompileProcessor processor = createProcessor(includes, cache);
                    return doIncrementalCompile(processor, spec);
                }
            });
        } finally {
            cache.close();
        }
    }

    protected abstract WorkResult doIncrementalCompile(IncrementalCompileProcessor processor, NativeCompileSpec spec);

    protected TaskInternal getTask() {
        return task;
    }

    private IncrementalCompileProcessor createProcessor(Iterable<File> includes, PersistentCache cache) {
        PersistentIndexedCache<File, FileState> stateCache = createCache(cache, "state", File.class, new DefaultSerializer<FileState>(FileState.class.getClassLoader()));
        // TODO:DAZ This doesn't need to be an indexed cache: need PersistentCache.createStateCache()
        PersistentIndexedCache<String, List<File>> listCache = createCache(cache, "previous", String.class, new DefaultSerializer<List<File>>());

        DefaultSourceDependencyParser dependencyParser = new DefaultSourceDependencyParser(includesParser, CollectionUtils.toList(includes));

        // TODO:DAZ Inject a factory, and come up with a common abstraction for TaskArtifactStateCacheAccess and PersistentCache
        CachingHasher hasher = new CachingHasher(new DefaultHasher(), cache);

        return new IncrementalCompileProcessor(stateCache, listCache, dependencyParser, hasher);
    }

    private PersistentCache openCache(TaskInternal task) {
        return cacheRepository
                .cache(task, "incrementalCompile")
                .withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive))
                .open();
    }

    private <U, V> PersistentIndexedCache<U, V> createCache(PersistentCache cache, String name, Class<U> keyType, Serializer<V> fileStateDefaultSerializer) {
        return cache.createCache(new PersistentIndexedCacheParameters<U, V>(name, keyType, fileStateDefaultSerializer));
    }

}
