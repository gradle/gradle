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
import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStateCache;
import org.gradle.internal.Factory;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;

abstract class AbstractIncrementalNativeCompiler implements Compiler<NativeCompileSpec> {
    private final TaskInternal task;
    private final SourceIncludesParser sourceIncludesParser;
    private final TaskArtifactStateCacheAccess cacheAccess;
    private final FileSnapshotter fileSnapshotter;
    private Iterable<File> includes;

    protected AbstractIncrementalNativeCompiler(TaskInternal task, SourceIncludesParser sourceIncludesParser, Iterable<File> includes,
                                                TaskArtifactStateCacheAccess cacheAccess, FileSnapshotter fileSnapshotter) {
        this.task = task;
        this.includes = includes;
        this.sourceIncludesParser = sourceIncludesParser;
        this.cacheAccess = cacheAccess;
        this.fileSnapshotter = fileSnapshotter;
    }

    public WorkResult execute(final NativeCompileSpec spec) {
        return cacheAccess.useCache("incremental compile", new Factory<WorkResult>() {
            public WorkResult create() {
                IncrementalCompileProcessor processor = createProcessor(includes);
                return doIncrementalCompile(processor, spec);
            }
        });
    }

    protected abstract WorkResult doIncrementalCompile(IncrementalCompileProcessor processor, NativeCompileSpec spec);

    protected TaskInternal getTask() {
        return task;
    }

    private IncrementalCompileProcessor createProcessor(Iterable<File> includes) {
        PersistentStateCache<CompilationState> compileStateCache = createCompileStateCache(task.getPath());

        DefaultSourceIncludesResolver dependencyParser = new DefaultSourceIncludesResolver(CollectionUtils.toList(includes));

        return new IncrementalCompileProcessor(compileStateCache, dependencyParser, sourceIncludesParser, fileSnapshotter);
    }

    private PersistentStateCache<CompilationState> createCompileStateCache(final String taskPath) {
        final PersistentIndexedCache<String, CompilationState> stateIndexedCache = cacheAccess.createCache("compilationState", String.class, new CompilationStateSerializer());
        return new PersistentStateCache<CompilationState>() {
            public CompilationState get() {
                return stateIndexedCache.get(taskPath);
            }

            public void set(CompilationState newValue) {
                stateIndexedCache.put(taskPath, newValue);
            }

            public void update(UpdateAction<CompilationState> updateAction) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
