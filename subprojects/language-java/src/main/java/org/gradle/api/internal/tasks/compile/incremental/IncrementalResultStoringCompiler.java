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
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JdkJavaCompilerResult;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotProvider;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.recomp.IncrementalCompilationResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.internal.Stash;
import org.gradle.language.base.internal.compile.Compiler;

import java.util.Map;
import java.util.Set;

/**
 * Stores the incremental class dependency analysis after compilation has finished.
 */
class IncrementalResultStoringCompiler<T extends JavaCompileSpec> implements Compiler<T> {

    private final Compiler<T> delegate;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;
    private final Stash<PreviousCompilationData> stash;
    private final StringInterner interner;

    IncrementalResultStoringCompiler(Compiler<T> delegate, ClasspathSnapshotProvider classpathSnapshotProvider, Stash<PreviousCompilationData> stash, StringInterner interner) {
        this.delegate = delegate;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
        this.stash = stash;
        this.interner = interner;
    }

    @Override
    public WorkResult execute(T spec) {
        WorkResult result = delegate.execute(spec);
        if (result instanceof RecompilationNotNecessary) {
            return result;
        }
        storeResult(spec, result);
        return result;
    }

    private void storeResult(JavaCompileSpec spec, WorkResult result) {
        ClasspathSnapshotData classpathSnapshot = classpathSnapshotProvider.getClasspathSnapshot(Iterables.concat(spec.getCompileClasspath(), spec.getModulePath())).getData();
        AnnotationProcessingData annotationProcessingData = getAnnotationProcessingResult(spec, result);
        PreviousCompilationData data = new PreviousCompilationData(spec.getDestinationDir(), annotationProcessingData, classpathSnapshot, spec.getAnnotationProcessorPath());
        stash.put(data);
    }

    private AnnotationProcessingData getAnnotationProcessingResult(JavaCompileSpec spec, WorkResult result) {
        Set<AnnotationProcessorDeclaration> processors = spec.getEffectiveAnnotationProcessors();
        if (processors == null || processors.isEmpty()) {
            return new AnnotationProcessingData();
        }
        if (result instanceof IncrementalCompilationResult) {
            result = ((IncrementalCompilationResult) result).getCompilerResult();
        }
        if (result instanceof JdkJavaCompilerResult) {
            AnnotationProcessingResult processingResult = ((JdkJavaCompilerResult) result).getAnnotationProcessingResult();
            return convertProcessingResult(processingResult);
        }
        return new AnnotationProcessingData(ImmutableMap.<String, Set<String>>of(), ImmutableSet.<String>of(), ImmutableSet.<String>of(), ImmutableMap.<String, Set<GeneratedResource>>of(), ImmutableSet.<GeneratedResource>of(), "the chosen compiler did not support incremental annotation processing");
    }

    private AnnotationProcessingData convertProcessingResult(AnnotationProcessingResult processingResult) {
        Map<String, Set<String>> generatedTypesByOrigin = processingResult.getGeneratedTypesWithIsolatedOrigin();
        Map<String, Set<GeneratedResource>> generatedResourcesByOrigin = processingResult.getGeneratedResourcesWithIsolatedOrigin();
        Set<String> aggregatedTypes = processingResult.getAggregatedTypes();
        Set<String> aggregatingTypes = processingResult.getGeneratedAggregatingTypes();
        Set<GeneratedResource> aggregatingResources = processingResult.getGeneratedAggregatingResources();
        return new AnnotationProcessingData(intern(generatedTypesByOrigin), intern(aggregatedTypes), intern(aggregatingTypes), generatedResourcesByOrigin, aggregatingResources, processingResult.getFullRebuildCause());
    }

    private Set<String> intern(Set<String> types) {
        Set<String> result = Sets.newHashSet();
        for (String string : types) {
            result.add(interner.intern(string));
        }
        return result;
    }

    private Map<String, Set<String>> intern(Map<String, Set<String>> types) {
        Map<String, Set<String>> result = Maps.newHashMap();
        for (Map.Entry<String, Set<String>> entry : types.entrySet()) {
            result.put(interner.intern(entry.getKey()), intern(entry.getValue()));
        }
        return result;
    }
}
