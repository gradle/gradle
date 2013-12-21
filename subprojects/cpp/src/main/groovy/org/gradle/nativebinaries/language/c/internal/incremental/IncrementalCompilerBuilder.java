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
import org.gradle.cache.CacheRepository;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.api.internal.tasks.compile.Compiler;

import java.io.File;

public class IncrementalCompilerBuilder {
    private final TaskInternal task;
    private final CacheRepository cacheRepository;
    private boolean cleanCompile;
    private Iterable<File> includes;

    public IncrementalCompilerBuilder(CacheRepository cacheRepository, TaskInternal task) {
        this.task = task;
        this.cacheRepository = cacheRepository;
    }

    public IncrementalCompilerBuilder withCleanCompile() {
        this.cleanCompile = true;
        return this;
    }

    public IncrementalCompilerBuilder withIncludes(Iterable<File> includes) {
        this.includes = includes;
        return this;
    }

    public Compiler<NativeCompileSpec> createIncrementalCompiler(Compiler<NativeCompileSpec> compiler) {
        if (cleanCompile) {
            return createCleaningCompiler(compiler, task, includes);
        }
        return createIncrementalCompiler(compiler, task, includes);
    }

    private Compiler<NativeCompileSpec> createIncrementalCompiler(Compiler<NativeCompileSpec> compiler, TaskInternal task, Iterable<File> includes) {
        return new IncrementalNativeCompiler(task, includes, cacheRepository, compiler);
    }

    private Compiler<NativeCompileSpec> createCleaningCompiler(Compiler<NativeCompileSpec> compiler, TaskInternal task, Iterable<File> includes) {
        return new CleanCompilingNativeCompiler(task, includes, cacheRepository, compiler);
    }
}
