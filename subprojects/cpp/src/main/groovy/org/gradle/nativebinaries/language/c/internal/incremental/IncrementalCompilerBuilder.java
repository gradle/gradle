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
import org.gradle.nativebinaries.language.c.internal.incremental.sourceparser.CSourceParser;
import org.gradle.nativebinaries.language.c.internal.incremental.sourceparser.RegexBackedCSourceParser;
import org.gradle.nativebinaries.language.objectivec.tasks.ObjectiveCCompile;
import org.gradle.nativebinaries.language.objectivecpp.tasks.ObjectiveCppCompile;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;

import java.io.File;

public class IncrementalCompilerBuilder {
    private final TaskInternal task;
    private final TaskArtifactStateCacheAccess cacheAccess;
    private final SourceIncludesParser sourceIncludesParser;
    private final FileSnapshotter fileSnapshotter;
    private boolean cleanCompile;
    private Iterable<File> includes;

    public IncrementalCompilerBuilder(TaskArtifactStateCacheAccess cacheAccess, FileSnapshotter fileSnapshotter, TaskInternal task) {
        this.task = task;
        this.sourceIncludesParser = createIncludesParser(task);
        this.cacheAccess = cacheAccess;
        this.fileSnapshotter = fileSnapshotter;
    }

    private static SourceIncludesParser createIncludesParser(TaskInternal task) {
        CSourceParser sourceParser = new RegexBackedCSourceParser();
        boolean importsAreIncludes = ObjectiveCCompile.class.isAssignableFrom(task.getClass()) || ObjectiveCppCompile.class.isAssignableFrom(task.getClass());
        return new DefaultSourceIncludesParser(sourceParser, importsAreIncludes);
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
        return new IncrementalNativeCompiler(task, sourceIncludesParser, includes, cacheAccess, fileSnapshotter, compiler);
    }

    private Compiler<NativeCompileSpec> createCleaningCompiler(Compiler<NativeCompileSpec> compiler, TaskInternal task, Iterable<File> includes) {
        return new CleanCompilingNativeCompiler(task, sourceIncludesParser, includes, cacheAccess, fileSnapshotter, compiler);
    }
}
