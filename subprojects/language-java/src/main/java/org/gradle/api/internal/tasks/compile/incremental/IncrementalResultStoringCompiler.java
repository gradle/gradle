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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JdkJavaCompilerResult;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotProvider;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.internal.Stash;
import org.gradle.language.base.internal.compile.Compiler;

import java.util.Set;

/**
 * Stores the incremental class dependency analysis after compilation has finished.
 */
class IncrementalResultStoringCompiler implements Compiler<JavaCompileSpec> {

    private final Compiler<JavaCompileSpec> delegate;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;
    private final Stash<PreviousCompilationData> stash;

    IncrementalResultStoringCompiler(Compiler<JavaCompileSpec> delegate, ClasspathSnapshotProvider classpathSnapshotProvider, Stash<PreviousCompilationData> stash) {
        this.delegate = delegate;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
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
        ClasspathSnapshotData classpathSnapshot = classpathSnapshotProvider.getClasspathSnapshot(spec.getCompileClasspath()).getData();
        AnnotationProcessingData annotationProcessingData = getAnnotationProcessingResult(spec, result);
        PreviousCompilationData data = new PreviousCompilationData(spec.getDestinationDir(), annotationProcessingData, classpathSnapshot, spec.getAnnotationProcessorPath());
        stash.put(data);
    }

    private AnnotationProcessingData getAnnotationProcessingResult(JavaCompileSpec spec, WorkResult result) {
        Set<AnnotationProcessorDeclaration> processors = spec.getEffectiveAnnotationProcessors();
        if (processors == null || processors.isEmpty()) {
            return new AnnotationProcessingData(ImmutableMap.<String, Set<String>>of(), ImmutableSet.<String>of(), ImmutableSet.<String>of(), null);
        }
        if (result instanceof JdkJavaCompilerResult) {
            AnnotationProcessingResult processingResult = ((JdkJavaCompilerResult) result).getAnnotationProcessingResult();
            return new AnnotationProcessingData(processingResult.getGeneratedTypesWithIsolatedOrigin(), processingResult.getAggregatedTypes(), processingResult.getGeneratedAggregatingTypes(), processingResult.getFullRebuildCause());
        }
        return new AnnotationProcessingData(ImmutableMap.<String, Set<String>>of(), ImmutableSet.<String>of(), ImmutableSet.<String>of(), "the chosen compiler did not support incremental annotation processing");
    }
}
