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

import com.google.common.collect.Iterables;
import org.gradle.api.internal.tasks.compile.ApiCompilerResult;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantsAnalysisResult;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilation;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilationAccess;
import org.gradle.api.internal.tasks.compile.incremental.recomp.DefaultIncrementalCompileResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilation;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationAccess;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.internal.tasks.compile.incremental.transaction.CompileTransaction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A compiler that selects classes for compilation. It also handles restore of output state in case of a compile failure.
 */
class SelectiveCompiler<T extends JavaCompileSpec> implements org.gradle.language.base.internal.compile.Compiler<T> {
    private static final Logger LOG = LoggerFactory.getLogger(SelectiveCompiler.class);
    private final CleaningJavaCompiler<T> cleaningCompiler;
    private final Compiler<T> rebuildAllCompiler;
    private final RecompilationSpecProvider recompilationSpecProvider;
    private final CurrentCompilationAccess classpathSnapshotter;
    private final PreviousCompilationAccess previousCompilationAccess;

    public SelectiveCompiler(
        CleaningJavaCompiler<T> cleaningJavaCompiler,
        Compiler<T> rebuildAllCompiler,
        RecompilationSpecProvider recompilationSpecProvider,
        CurrentCompilationAccess classpathSnapshotter,
        PreviousCompilationAccess previousCompilationAccess
    ) {
        this.cleaningCompiler = cleaningJavaCompiler;
        this.rebuildAllCompiler = rebuildAllCompiler;
        this.recompilationSpecProvider = recompilationSpecProvider;
        this.classpathSnapshotter = classpathSnapshotter;
        this.previousCompilationAccess = previousCompilationAccess;
    }

    @Override
    public WorkResult execute(T spec) {
        if (!recompilationSpecProvider.isIncremental()) {
            LOG.info("Full recompilation is required because no incremental change information is available. This is usually caused by clean builds or changing compiler arguments.");
            return rebuildAllCompiler.execute(spec);
        }
        File previousCompilationDataFile = Objects.requireNonNull(spec.getCompileOptions().getPreviousCompilationDataFile());
        if (!previousCompilationDataFile.exists()) {
            LOG.info("Full recompilation is required because no previous compilation result is available.");
            return rebuildAllCompiler.execute(spec);
        }
        if (spec.getSourceRoots().isEmpty()) {
            LOG.info("Full recompilation is required because the source roots could not be inferred.");
            return rebuildAllCompiler.execute(spec);
        }

        Timer clock = Time.startTimer();
        CurrentCompilation currentCompilation = new CurrentCompilation(spec, classpathSnapshotter);

        PreviousCompilationData previousCompilationData = previousCompilationAccess.readPreviousCompilationData(previousCompilationDataFile);
        PreviousCompilation previousCompilation = new PreviousCompilation(previousCompilationData);
        RecompilationSpec recompilationSpec = recompilationSpecProvider.provideRecompilationSpec(spec, currentCompilation, previousCompilation);

        if (recompilationSpec.isFullRebuildNeeded()) {
            LOG.info("Full recompilation is required because {}. Analysis took {}.", recompilationSpec.getFullRebuildCause(), clock.getElapsed());
            return rebuildAllCompiler.execute(spec);
        }

        CompileTransaction transaction = recompilationSpecProvider.initCompilationSpecAndTransaction(spec, recompilationSpec);
        return transaction.execute(workResult -> {
            if (Iterables.isEmpty(spec.getSourceFiles()) && spec.getClassesToProcess().isEmpty()) {
                LOG.info("None of the classes needs to be compiled! Analysis took {}. ", clock.getElapsed());
                return new RecompilationNotNecessary(previousCompilationData, recompilationSpec);
            }
            try {
                WorkResult result = cleaningCompiler.getCompiler().execute(spec);
                Set<String> alreadyCompiledClasses = new HashSet<>(spec.getClassesToCompile());
                result = recompilationSpecProvider.decorateResult(recompilationSpec, previousCompilationData, result);
                result = recompileDependents(result, spec, alreadyCompiledClasses, previousCompilationData, previousCompilation, clock);
                return result.or(workResult);
            } finally {
                Collection<String> classesToCompile = recompilationSpec.getClassesToCompile();
                LOG.info("Incremental compilation of {} classes completed in {}.", classesToCompile.size(), clock.getElapsed());
                LOG.debug("Recompiled classes {}", classesToCompile);
            }
        });
    }

    private WorkResult recompileDependents(
        WorkResult result,
        T spec,
        Set<String> alreadyCompiledClasses,
        PreviousCompilationData previousCompilationData,
        PreviousCompilation previousCompilation,
        Timer clock
    ) {
        CurrentCompilation currentCompilation = new CurrentCompilation(spec, classpathSnapshotter);
        RecompilationSpec newRecompilationSpec = recompilationSpecProvider.provideAbiDependentRecompilationSpec(spec, currentCompilation, previousCompilation, alreadyCompiledClasses);

        if (newRecompilationSpec.isFullRebuildNeeded()) {
            LOG.info("Full recompilation is required because {}. Analysis took {}.", newRecompilationSpec.getFullRebuildCause(), clock.getElapsed());
            return rebuildAllCompiler.execute(spec);
        }

        recompilationSpecProvider.initCompilationSpecAndTransaction(spec, newRecompilationSpec);
        if (Iterables.isEmpty(spec.getSourceFiles()) && spec.getClassesToProcess().isEmpty()) {
            // No dependents need recompilation
            return result;
        }
        WorkResult dependentCompileResult = cleaningCompiler.getCompiler().execute(spec);
        alreadyCompiledClasses.addAll(spec.getClassesToCompile());
        dependentCompileResult = recompileDependents(dependentCompileResult, spec, alreadyCompiledClasses, previousCompilationData, previousCompilation, clock);
        dependentCompileResult = recompilationSpecProvider.decorateResult(newRecompilationSpec, previousCompilationData, dependentCompileResult);
        return combine(result, dependentCompileResult).or(result);
    }

    private static WorkResult combine(WorkResult result, WorkResult dependentCompileResult) {
        if (result instanceof ApiCompilerResult && dependentCompileResult instanceof ApiCompilerResult) {
            ApiCompilerResult apiCompilerResult = (ApiCompilerResult) result;
            ApiCompilerResult dependentApiCompilerResult = (ApiCompilerResult) dependentCompileResult;
            ApiCompilerResult combined = new ApiCompilerResult();

            addAnnotationProcessingResult(combined.getAnnotationProcessingResult(), apiCompilerResult.getAnnotationProcessingResult());
            addAnnotationProcessingResult(combined.getAnnotationProcessingResult(), dependentApiCompilerResult.getAnnotationProcessingResult());
            addConstantsAnalysisResult(combined.getConstantsAnalysisResult(), apiCompilerResult.getConstantsAnalysisResult());
            addConstantsAnalysisResult(combined.getConstantsAnalysisResult(), dependentApiCompilerResult.getConstantsAnalysisResult());
            addSourceClassesMapping(combined.getSourceClassesMapping(), apiCompilerResult.getSourceClassesMapping());
            addSourceClassesMapping(combined.getSourceClassesMapping(), dependentApiCompilerResult.getSourceClassesMapping());

            combined.getBackupClassFiles().putAll(apiCompilerResult.getBackupClassFiles());
            combined.getBackupClassFiles().putAll(dependentApiCompilerResult.getBackupClassFiles());
            return combined;
        }
        if (result instanceof DefaultIncrementalCompileResult && dependentCompileResult instanceof DefaultIncrementalCompileResult) {
            DefaultIncrementalCompileResult defaultIncrementalCompileResult = (DefaultIncrementalCompileResult) result;
            DefaultIncrementalCompileResult dependentDefaultIncrementalCompileResult = (DefaultIncrementalCompileResult) dependentCompileResult;
            RecompilationSpec recompilationSpec = new RecompilationSpec();
            recompilationSpec.addClassesToCompile(defaultIncrementalCompileResult.getRecompilationSpec().getClassesToCompile());
            recompilationSpec.addClassesToCompile(dependentDefaultIncrementalCompileResult.getRecompilationSpec().getClassesToCompile());
            recompilationSpec.addSourcePaths(defaultIncrementalCompileResult.getRecompilationSpec().getSourcePaths());
            recompilationSpec.addSourcePaths(dependentDefaultIncrementalCompileResult.getRecompilationSpec().getSourcePaths());
            for (String classesToProcess : defaultIncrementalCompileResult.getRecompilationSpec().getClassesToProcess()) {
                recompilationSpec.addClassToReprocess(classesToProcess);
            }
            for (String classesToProcess : dependentDefaultIncrementalCompileResult.getRecompilationSpec().getClassesToProcess()) {
                recompilationSpec.addClassToReprocess(classesToProcess);
            }
            recompilationSpec.addResourcesToGenerate(defaultIncrementalCompileResult.getRecompilationSpec().getResourcesToGenerate());
            recompilationSpec.addResourcesToGenerate(dependentDefaultIncrementalCompileResult.getRecompilationSpec().getResourcesToGenerate());

            return new DefaultIncrementalCompileResult(
                defaultIncrementalCompileResult.getPreviousCompilationData(),
                recompilationSpec,
                combine(defaultIncrementalCompileResult.getCompilerResult(), dependentDefaultIncrementalCompileResult.getCompilerResult())
            );
        }
        return result;
    }

    private static void addAnnotationProcessingResult(AnnotationProcessingResult result, AnnotationProcessingResult origin) {
        for (Map.Entry<String, Set<String>> entry : origin.getGeneratedTypesWithIsolatedOrigin().entrySet()) {
            result.addGeneratedType(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Set<GeneratedResource>> entry : origin.getGeneratedResourcesWithIsolatedOrigin().entrySet()) {
            for (GeneratedResource generatedResource : entry.getValue()) {
                result.addGeneratedResource(generatedResource, Collections.singleton(entry.getKey()));
            }
        }
        result.getAggregatedTypes().addAll(origin.getAggregatedTypes());
        result.getGeneratedAggregatingTypes().addAll(origin.getGeneratedAggregatingTypes());
        result.getGeneratedAggregatingResources().addAll(origin.getGeneratedAggregatingResources());
        result.getAnnotationProcessorResults().addAll(origin.getAnnotationProcessorResults());
        if (result.getFullRebuildCause() == null) {
            result.setFullRebuildCause(origin.getFullRebuildCause());
        }
    }

    private static void addConstantsAnalysisResult(ConstantsAnalysisResult result, ConstantsAnalysisResult origin) {
        Optional<ConstantToDependentsMapping> constantToDependentsMapping = origin.getConstantToDependentsMapping();
        if (constantToDependentsMapping.isPresent()) {
            for (Map.Entry<String, DependentsSet> entry : constantToDependentsMapping.get().getConstantDependents().entrySet()) {
                for (String privateDependentClass : entry.getValue().getPrivateDependentClasses()) {
                    result.addPrivateDependent(entry.getKey(), privateDependentClass);
                }
                for (String accessibleDependentClass : entry.getValue().getAccessibleDependentClasses()) {
                    result.addPublicDependent(entry.getKey(), accessibleDependentClass);
                }
            }
        }
    }

    private static void addSourceClassesMapping(Map<String, Set<String>> result, Map<String, Set<String>> origin) {
        for (Map.Entry<String, Set<String>> entry : origin.entrySet()) {
            result.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
        }
    }
}
