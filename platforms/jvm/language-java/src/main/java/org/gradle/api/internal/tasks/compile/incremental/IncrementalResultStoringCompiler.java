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
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.compile.ApiCompilerResult;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMappingMerger;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilationAccess;
import org.gradle.api.internal.tasks.compile.incremental.recomp.IncrementalCompilationResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationAccess;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stores the incremental class dependency analysis after compilation has finished.
 */
class IncrementalResultStoringCompiler<T extends JavaCompileSpec> implements Compiler<T> {

    private final Compiler<T> delegate;
    private final CurrentCompilationAccess classpathSnapshotter;
    private final PreviousCompilationAccess previousCompilationAccess;

    IncrementalResultStoringCompiler(Compiler<T> delegate, CurrentCompilationAccess classpathSnapshotter, PreviousCompilationAccess previousCompilationAccess) {
        this.delegate = delegate;
        this.classpathSnapshotter = classpathSnapshotter;
        this.previousCompilationAccess = previousCompilationAccess;
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
        ClassSetAnalysisData outputSnapshot = classpathSnapshotter.analyzeOutputFolder(spec.getDestinationDir());
        ClassSetAnalysisData classpathSnapshot = classpathSnapshotter.getClasspathSnapshot(Iterables.concat(spec.getCompileClasspath(), spec.getModulePath()));
        AnnotationProcessingData annotationProcessingData = getAnnotationProcessingData(spec, result);
        CompilerApiData compilerApiData = getCompilerApiData(spec, result);
        ClassSetAnalysisData minimizedClasspathSnapshot = classpathSnapshot.reduceToTypesAffecting(outputSnapshot, compilerApiData);
        PreviousCompilationData data = new PreviousCompilationData(outputSnapshot, annotationProcessingData, minimizedClasspathSnapshot, compilerApiData);
        File previousCompilationDataFile = Objects.requireNonNull(spec.getCompileOptions().getPreviousCompilationDataFile());
        previousCompilationAccess.writePreviousCompilationData(data, previousCompilationDataFile);
    }

    private CompilerApiData getCompilerApiData(JavaCompileSpec spec, WorkResult result) {
        if (spec.getCompileOptions().supportsCompilerApi()) {
            CompilerApiData previousCompilerApiData = null;
            RecompilationSpec recompilationSpec = null;
            if (result instanceof IncrementalCompilationResult) {
                previousCompilerApiData = ((IncrementalCompilationResult) result).getPreviousCompilationData().getCompilerApiData();
                recompilationSpec = ((IncrementalCompilationResult) result).getRecompilationSpec();
                result = ((IncrementalCompilationResult) result).getCompilerResult();
            }

            Set<String> changedClasses = recompilationSpec == null ? Collections.emptySet() : recompilationSpec.getClassesToCompile();
            ConstantToDependentsMapping previousConstantToDependentsMapping = previousCompilerApiData == null ? null : previousCompilerApiData.getConstantToClassMapping();
            Map<String, Set<String>> previousSourceClassesMapping = previousCompilerApiData == null ? null : previousCompilerApiData.getSourceToClassMapping();
            if (result instanceof ApiCompilerResult) {
                ApiCompilerResult jdkJavaResult = (ApiCompilerResult) result;
                ConstantToDependentsMapping newConstantsToDependentsMapping = jdkJavaResult.getConstantsAnalysisResult()
                    .getConstantToDependentsMapping()
                    .orElseThrow(() -> new GradleException("Constants to dependents mapping not present, but it should be"));
                Map<String, Set<String>> newSourceClassesMapping = jdkJavaResult.getSourceClassesMapping();
                Map<String, Set<String>> mergedSourceClassesMapping;
                if (previousSourceClassesMapping == null) {
                    mergedSourceClassesMapping = newSourceClassesMapping;
                } else {
                    mergedSourceClassesMapping = mergeSourceClassesMappings(previousSourceClassesMapping, newSourceClassesMapping, changedClasses);
                }
                ConstantToDependentsMapping mergedConstants = new ConstantToDependentsMappingMerger().merge(newConstantsToDependentsMapping, previousConstantToDependentsMapping, changedClasses);
                if (spec.getCompileOptions().supportsConstantAnalysis()) {
                    return CompilerApiData.withConstantsMapping(mergedSourceClassesMapping, mergedConstants);
                } else {
                    return CompilerApiData.withoutConstantsMapping(mergedSourceClassesMapping);
                }
            }
        }
        return CompilerApiData.unavailable();
    }

    private Map<String, Set<String>> mergeSourceClassesMappings(Map<String, Set<String>> previousSourceClassesMapping, Map<String, Set<String>> newSourceClassesMapping, Set<String> changedClasses) {
        Map<String, Set<String>> merged = new HashMap<>(previousSourceClassesMapping);
        merged.keySet().removeAll(changedClasses);
        for (Map.Entry<String, Set<String>> entry : newSourceClassesMapping.entrySet()) {
            merged.computeIfAbsent(entry.getKey(), key -> new HashSet<>()).addAll(entry.getValue());
        }
        return merged;
    }

    private AnnotationProcessingData getAnnotationProcessingData(JavaCompileSpec spec, WorkResult result) {
        Set<AnnotationProcessorDeclaration> processors = spec.getEffectiveAnnotationProcessors();
        if (processors.isEmpty()) {
            return new AnnotationProcessingData();
        }
        AnnotationProcessingData previousAnnotationProcessingData = null;
        RecompilationSpec recompilationSpec = null;
        if (result instanceof IncrementalCompilationResult) {
            previousAnnotationProcessingData = ((IncrementalCompilationResult) result).getPreviousCompilationData().getAnnotationProcessingData();
            recompilationSpec = ((IncrementalCompilationResult) result).getRecompilationSpec();
            result = ((IncrementalCompilationResult) result).getCompilerResult();
        }
        Set<String> changedClasses = recompilationSpec == null ? Collections.emptySet() : recompilationSpec.getClassesToCompile();

        if (result instanceof ApiCompilerResult) {
            AnnotationProcessingResult processingResult = ((ApiCompilerResult) result).getAnnotationProcessingResult();
            AnnotationProcessingData newAnnotationProcessingData = new AnnotationProcessingData(
                processingResult.getGeneratedTypesWithIsolatedOrigin(),
                processingResult.getAggregatedTypes(),
                processingResult.getGeneratedAggregatingTypes(),
                processingResult.getGeneratedResourcesWithIsolatedOrigin(),
                processingResult.getGeneratedAggregatingResources(),
                processingResult.getFullRebuildCause()
            );
            if (previousAnnotationProcessingData == null) {
                return newAnnotationProcessingData;
            }
            return mergeAnnotationProcessingData(previousAnnotationProcessingData, newAnnotationProcessingData, changedClasses);
        }
        return new AnnotationProcessingData(
            ImmutableMap.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableMap.of(),
            ImmutableSet.of(),
            "the chosen compiler did not support incremental annotation processing"
        );

    }

    private AnnotationProcessingData mergeAnnotationProcessingData(AnnotationProcessingData oldData, AnnotationProcessingData newData, Set<String> changedClasses) {
        Map<String, Set<String>> generatedTypesByOrigin = new HashMap<>(oldData.getGeneratedTypesByOrigin());
        changedClasses.forEach(generatedTypesByOrigin::remove);
        generatedTypesByOrigin.putAll(newData.getGeneratedTypesByOrigin());
        Map<String, Set<GeneratedResource>> generatedResourcesByOrigin = new HashMap<>(oldData.getGeneratedResourcesByOrigin());
        changedClasses.forEach(generatedResourcesByOrigin::remove);
        generatedResourcesByOrigin.putAll(newData.getGeneratedResourcesByOrigin());

        return new AnnotationProcessingData(
            generatedTypesByOrigin,
            newData.getAggregatedTypes(),
            newData.getGeneratedTypesDependingOnAllOthers(),
            generatedResourcesByOrigin,
            newData.getGeneratedResourcesDependingOnAllOthers(),
            newData.getFullRebuildCause()
        );
    }

}
