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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.CompilationOutputAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotProvider;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.internal.Stash;
import org.gradle.language.base.internal.compile.Compiler;

/**
 * Stores the incremental class dependency analysis after compilation has finished.
 */
class IncrementalResultStoringCompiler implements Compiler<JavaCompileSpec> {

    private final Compiler<JavaCompileSpec> delegate;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;
    private final Stash<PreviousCompilationData> stash;
    private final CompilationOutputAnalyzer compilationOutputAnalyzer;

    IncrementalResultStoringCompiler(Compiler<JavaCompileSpec> delegate, ClasspathSnapshotProvider classpathSnapshotProvider, CompilationOutputAnalyzer compilationOutputAnalyzer, Stash<PreviousCompilationData> stash) {
        this.delegate = delegate;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
        this.compilationOutputAnalyzer = compilationOutputAnalyzer;
        this.stash = stash;
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        WorkResult result = delegate.execute(spec);
        if (result instanceof RecompilationNotNecessary) {
            return result;
        }
        storeResult(spec, result);
        return result;
    }

    private void storeResult(JavaCompileSpec spec, WorkResult result) {
        ClassSetAnalysisData classAnalysis = compilationOutputAnalyzer.getAnalysis(spec, result);
        ClasspathSnapshotData classpathSnapshot = classpathSnapshotProvider.getClasspathSnapshot(spec.getCompileClasspath()).getData();
        PreviousCompilationData data = new PreviousCompilationData(classAnalysis, classpathSnapshot, spec.getAnnotationProcessorPath());
        stash.put(data);
    }
}
