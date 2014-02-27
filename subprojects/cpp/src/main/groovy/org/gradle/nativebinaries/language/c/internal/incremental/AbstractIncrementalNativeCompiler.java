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
import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;

abstract class AbstractIncrementalNativeCompiler implements Compiler<NativeCompileSpec> {
    private final TaskInternal task;
    private final SourceIncludesParser sourceIncludesParser;
    private final CacheRepository cacheRepository;
    private Iterable<File> includes;

    protected AbstractIncrementalNativeCompiler(TaskInternal task, SourceIncludesParser sourceIncludesParser, Iterable<File> includes, CacheRepository cacheRepository) {
        this.task = task;
        this.includes = includes;
        this.sourceIncludesParser = sourceIncludesParser;
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
        // TODO:DAZ This doesn't need to be an indexed cache: need PersistentCache.createStateCache()
        PersistentIndexedCache<String, CompilationState> listCache = createCache(cache, "previous", new CompilationStateSerializer());

        DefaultSourceIncludesResolver dependencyParser = new DefaultSourceIncludesResolver(CollectionUtils.toList(includes));

        // TODO:DAZ Inject a factory, and come up with a common abstraction for TaskArtifactStateCacheAccess and PersistentCache
        FileSnapshotter snapshotter = new CachingFileSnapshotter(new DefaultHasher(), cache);

        return new IncrementalCompileProcessor(listCache, dependencyParser, sourceIncludesParser, snapshotter);
    }

    private PersistentCache openCache(TaskInternal task) {
        return cacheRepository
                .cache(task, "incrementalCompile")
                .withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive))
                .open();
    }

    private <V> PersistentIndexedCache<String, V> createCache(PersistentCache cache, String name, Serializer<V> fileStateDefaultSerializer) {
        return cache.createCache(new PersistentIndexedCacheParameters<String, V>(name, String.class, fileStateDefaultSerializer));
    }

}
