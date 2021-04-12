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
import com.google.common.collect.Multimap;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JdkJavaCompilerResult;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToClassMapping;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToClassMappingMerger;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilationAccess;
import org.gradle.api.internal.tasks.compile.incremental.recomp.IncrementalCompilationResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationAccess;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.work.InputChanges;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.gradle.api.internal.tasks.compile.SourceClassesMappingFileAccessor.mergeIncrementalMappingsIntoOldMappings;
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantsMappingFileAccessor.readConstantsClassesMappingFile;

/**
 * Stores the incremental class dependency analysis after compilation has finished.
 */
class IncrementalResultStoringCompiler<T extends JavaCompileSpec> implements Compiler<T> {

    private final Compiler<T> delegate;
    private final CurrentCompilationAccess classpathSnapshotter;
    private final PreviousCompilationAccess previousCompilationAccess;
    private final InputChanges inputChanges;
    private final FileCollection stableSources;

    IncrementalResultStoringCompiler(Compiler<T> delegate, CurrentCompilationAccess classpathSnapshotter, PreviousCompilationAccess previousCompilationAccess, InputChanges inputChanges, FileCollection stableSources) {
        this.delegate = delegate;
        this.classpathSnapshotter = classpathSnapshotter;
        this.previousCompilationAccess = previousCompilationAccess;
        this.inputChanges = inputChanges;
        this.stableSources = stableSources;
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
        AnnotationProcessingData annotationProcessingData = getAnnotationProcessingResult(spec, result);
        ClassSetAnalysisData minimizedClasspathSnapshot = classpathSnapshot.reduceToTypesAffecting(outputSnapshot);
        CompilerApiData compilerApiData = getCompilerApiData(spec, result);
        PreviousCompilationData data = new PreviousCompilationData(outputSnapshot, annotationProcessingData, minimizedClasspathSnapshot, compilerApiData);
        File previousCompilationDataFile = Objects.requireNonNull(spec.getCompileOptions().getPreviousCompilationDataFile());
        previousCompilationAccess.writePreviousCompilationData(data, previousCompilationDataFile);
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
        return new AnnotationProcessingData(
            processingResult.getGeneratedTypesWithIsolatedOrigin(),
            processingResult.getAggregatedTypes(),
            processingResult.getGeneratedAggregatingTypes(),
            processingResult.getGeneratedResourcesWithIsolatedOrigin(),
            processingResult.getGeneratedAggregatingResources(),
            processingResult.getFullRebuildCause()
        );
    }

    private CompilerApiData getCompilerApiData(JavaCompileSpec spec, WorkResult result) {
        if (spec.getCompileOptions().supportsCompilerApi()) {
            Set<String> removedClasses = mergeClassFileMappingAndReturnRemovedClasses(spec);
            ConstantToClassMapping previousConstantToClassMapping = null;
            if (result instanceof IncrementalCompilationResult) {
                previousConstantToClassMapping = ((IncrementalCompilationResult) result).getPreviousCompilationData().getCompilerApiData().getConstantToClassMapping();
                result = ((IncrementalCompilationResult) result).getCompilerResult();
            }
            if (result instanceof JdkJavaCompilerResult) {
                ConstantToClassMapping constantToDependentsMapping = ((JdkJavaCompilerResult) result).getConstantsAnalysisResult().getConstantsToClassMapping();
                ConstantToClassMapping newConstantsMapping = new ConstantToClassMappingMerger().merge(constantToDependentsMapping, previousConstantToClassMapping, removedClasses);
                return CompilerApiData.availableOf(newConstantsMapping);
            }
        }
        return CompilerApiData.unavailableOf();
    }

    private Set<String> mergeClassFileMappingAndReturnRemovedClasses(JavaCompileSpec spec) {
        File classToFileMappingFile = Objects.requireNonNull(spec.getCompileOptions().getIncrementalCompilationMappingFile());
        Multimap<String, String> previousMappings = spec.getCompileOptions().getPreviousIncrementalCompilationMapping();

        if (previousMappings != null) {
            // The compilation will generate the new mapping file
            // Only merge old mappings into new mapping on incremental recompilation
            return mergeIncrementalMappingsIntoOldMappings(classToFileMappingFile, stableSources, inputChanges, previousMappings);
        }

        // Compilation was not incremental
        return Collections.emptySet();
    }

}
