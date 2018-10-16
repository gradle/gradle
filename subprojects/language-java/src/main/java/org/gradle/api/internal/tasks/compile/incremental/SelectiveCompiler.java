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
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotProvider;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilation;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilation;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.language.base.internal.compile.Compiler;

import java.util.Collection;

class SelectiveCompiler implements org.gradle.language.base.internal.compile.Compiler<JavaCompileSpec> {
    private static final Logger LOG = Logging.getLogger(SelectiveCompiler.class);
    private final IncrementalTaskInputs inputs;
    private final PreviousCompilation previousCompilation;
    private final CleaningJavaCompiler cleaningCompiler;
    private final Compiler<JavaCompileSpec> rebuildAllCompiler;
    private final RecompilationSpecProvider recompilationSpecProvider;
    private final IncrementalCompilationInitializer incrementalCompilationInitializer;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;

    public SelectiveCompiler(IncrementalTaskInputs inputs, PreviousCompilation previousCompilation, CleaningJavaCompiler cleaningCompiler,
                             Compiler<JavaCompileSpec> rebuildAllCompiler, RecompilationSpecProvider recompilationSpecProvider, IncrementalCompilationInitializer compilationInitializer, ClasspathSnapshotProvider classpathSnapshotProvider) {
        this.inputs = inputs;
        this.previousCompilation = previousCompilation;
        this.cleaningCompiler = cleaningCompiler;
        this.rebuildAllCompiler = rebuildAllCompiler;
        this.recompilationSpecProvider = recompilationSpecProvider;
        this.incrementalCompilationInitializer = compilationInitializer;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        Timer clock = Time.startTimer();
        CurrentCompilation currentCompilation = new CurrentCompilation(inputs, spec, classpathSnapshotProvider);

        RecompilationSpec recompilationSpec = recompilationSpecProvider.provideRecompilationSpec(currentCompilation, previousCompilation);

        if (recompilationSpec.isFullRebuildNeeded()) {
            LOG.info("Full recompilation is required because {}. Analysis took {}.", recompilationSpec.getFullRebuildCause(), clock.getElapsed());
            return rebuildAllCompiler.execute(spec);
        }

        incrementalCompilationInitializer.initializeCompilation(spec, recompilationSpec);

        if (Iterables.isEmpty(spec.getSourceFiles()) && spec.getClasses().isEmpty()) {
            LOG.info("None of the classes needs to be compiled! Analysis took {}. ", clock.getElapsed());
            return new RecompilationNotNecessary();
        }

        try {
            return cleaningCompiler.getCompiler().execute(spec);
        } finally {
            Collection<String> classesToCompile = recompilationSpec.getClassesToCompile();
            LOG.info("Incremental compilation of {} classes completed in {}.", classesToCompile.size(), clock.getElapsed());
            LOG.debug("Recompiled classes {}", classesToCompile);
        }
    }
}
