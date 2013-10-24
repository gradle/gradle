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
import org.gradle.api.internal.changedetection.state.Hasher;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.List;

public class IncrementalCompilerBuilder {
    private final RegexBackedIncludesParser includesParser = new RegexBackedIncludesParser();
    private final TaskInternal task;
    private final CacheRepository cacheRepository;
    private final Hasher hasher;
    private boolean cleanCompile;
    private Iterable<File> includes;

    public IncrementalCompilerBuilder(CacheRepository cacheRepository, Hasher hasher, TaskInternal task) {
        this.task = task;
        this.cacheRepository = cacheRepository;
        this.hasher = hasher;
    }

    public IncrementalCompilerBuilder withCleanCompile() {
        this.cleanCompile = true;
        return this;
    }

    public IncrementalCompilerBuilder withIncludes(Iterable<File> includes) {
        this.includes = includes;
        return this;
    }

    public org.gradle.api.internal.tasks.compile.Compiler<NativeCompileSpec> createIncrementalCompiler(org.gradle.api.internal.tasks.compile.Compiler<NativeCompileSpec> compiler) {
        if (cleanCompile) {
            return createCleaningCompiler(compiler, task, includes);
        }
        return createIncrementalCompiler(compiler, task, includes);
    }

    private org.gradle.api.internal.tasks.compile.Compiler<NativeCompileSpec> createIncrementalCompiler(org.gradle.api.internal.tasks.compile.Compiler<NativeCompileSpec> compiler, TaskInternal task, Iterable<File> includes) {
        IncrementalCompileProcessor incrementalProcessor = createProcessor(task, includes);
        IncrementalNativeCompiler incrementalNativeCompiler = new IncrementalNativeCompiler(compiler, incrementalProcessor);
        return new CacheLockingIncrementalCompiler(incrementalProcessor.getCacheAccess(), incrementalNativeCompiler);
    }

    private org.gradle.api.internal.tasks.compile.Compiler<NativeCompileSpec> createCleaningCompiler(org.gradle.api.internal.tasks.compile.Compiler<NativeCompileSpec> compiler, TaskInternal task, Iterable<File> includes) {
        IncrementalCompileProcessor incrementalProcessor = createProcessor(task, includes);
        CleanCompilingNativeCompiler cleanCompilingNativeCompiler = new CleanCompilingNativeCompiler(compiler, incrementalProcessor, task.getOutputs());
        return new CacheLockingIncrementalCompiler(incrementalProcessor.getCacheAccess(), cleanCompilingNativeCompiler);
    }

    private IncrementalCompileProcessor createProcessor(TaskInternal task, Iterable<File> includes) {
        LockOptions lockOptions = LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive);
        PersistentCache cache = cacheRepository.cache("incrementalCompile").forObject(task).withLockOptions(lockOptions).open();

        PersistentIndexedCache<File, FileState> stateCache = createCache(cache, "state", File.class, new DefaultSerializer<FileState>(FileState.class.getClassLoader()));
        // TODO:DAZ This doesn't need to be an indexed cache: need PersistentCache.createStateCache()
        PersistentIndexedCache<String, List<File>> listCache = createCache(cache, "previous", String.class, new DefaultSerializer<List<File>>());

        DefaultSourceDependencyParser dependencyParser = new DefaultSourceDependencyParser(includesParser, CollectionUtils.toList(includes));
        return new IncrementalCompileProcessor(cache, stateCache, listCache, dependencyParser, hasher);
    }

    private <U, V> PersistentIndexedCache<U, V> createCache(PersistentCache cache, String name, Class<U> keyType, DefaultSerializer<V> fileStateDefaultSerializer) {
        File cacheFile = new File(cache.getBaseDir(), name + ".bin");
        return cache.createCache(new PersistentIndexedCacheParameters<U, V>(cacheFile, keyType, fileStateDefaultSerializer));
    }

}
