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
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilation;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilationAccess;
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
import java.util.Objects;

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
                result = recompilationSpecProvider.decorateResult(recompilationSpec, previousCompilationData, result);
                return result.or(workResult);
            } finally {
                Collection<String> classesToCompile = recompilationSpec.getClassesToCompile();
                LOG.info("Incremental compilation of {} classes completed in {}.", classesToCompile.size(), clock.getElapsed());
                LOG.debug("Recompiled classes {}", classesToCompile);
            }
        });
    }
}
