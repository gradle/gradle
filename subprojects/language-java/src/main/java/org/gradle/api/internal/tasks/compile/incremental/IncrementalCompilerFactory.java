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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClassSetAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilationAccess;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationAccess;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.language.base.internal.compile.Compiler;

public class IncrementalCompilerFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final StringInterner interner;
    private final ClassSetAnalyzer classSetAnalyzer;

    public IncrementalCompilerFactory(BuildOperationExecutor buildOperationExecutor, StringInterner interner, ClassSetAnalyzer classSetAnalyzer) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.interner = interner;
        this.classSetAnalyzer = classSetAnalyzer;
    }

    public <T extends JavaCompileSpec> Compiler<T> makeIncremental(CleaningJavaCompiler<T> cleaningJavaCompiler, FileTree sources, RecompilationSpecProvider recompilationSpecProvider) {
        Compiler<T> rebuildAllCompiler = createRebuildAllCompiler(cleaningJavaCompiler, sources);
        CurrentCompilationAccess currentCompilationAccess = new CurrentCompilationAccess(classSetAnalyzer, buildOperationExecutor);
        PreviousCompilationAccess previousCompilationAccess = new PreviousCompilationAccess(interner);
        Compiler<T> compiler = new SelectiveCompiler<>(cleaningJavaCompiler, rebuildAllCompiler, recompilationSpecProvider, currentCompilationAccess, previousCompilationAccess);
        return new IncrementalResultStoringCompiler<>(compiler, currentCompilationAccess, previousCompilationAccess);
    }

    private <T extends JavaCompileSpec> Compiler<T> createRebuildAllCompiler(CleaningJavaCompiler<T> cleaningJavaCompiler, FileTree sourceFiles) {
        return spec -> {
            spec.setSourceFiles(sourceFiles);
            return cleaningJavaCompiler.execute(spec);
        };
    }
}
