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
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

class SelectiveCompiler<T extends JavaCompileSpec> implements org.gradle.language.base.internal.compile.Compiler<T> {
    private static final Logger LOG = LoggerFactory.getLogger(SelectiveCompiler.class);
    private final PreviousCompilation previousCompilation;
    private final CleaningJavaCompiler<T> cleaningCompiler;
    private final Compiler<T> rebuildAllCompiler;
    private final RecompilationSpecProvider recompilationSpecProvider;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;

    public SelectiveCompiler(PreviousCompilation previousCompilation,
                             CleaningJavaCompiler<T> cleaningJavaCompiler,
                             Compiler<T> rebuildAllCompiler,
                             RecompilationSpecProvider recompilationSpecProvider,
                             ClasspathSnapshotProvider classpathSnapshotProvider) {
        this.previousCompilation = previousCompilation;
        this.cleaningCompiler = cleaningJavaCompiler;
        this.rebuildAllCompiler = rebuildAllCompiler;
        this.recompilationSpecProvider = recompilationSpecProvider;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
    }

    @Override
    public WorkResult execute(T spec) {
        if (spec.getSourceRoots().isEmpty()) {
            LOG.info("Full recompilation is required because the source roots could not be inferred.");
            return rebuildAllCompiler.execute(spec);
        }

        Timer clock = Time.startTimer();
        CurrentCompilation currentCompilation = new CurrentCompilation(spec, classpathSnapshotProvider);

        RecompilationSpec recompilationSpec = recompilationSpecProvider.provideRecompilationSpec(currentCompilation, previousCompilation);

        if (recompilationSpec.isFullRebuildNeeded()) {
            LOG.info("Full recompilation is required because {}. Analysis took {}.", recompilationSpec.getFullRebuildCause(), clock.getElapsed());
            return rebuildAllCompiler.execute(spec);
        }

        boolean cleanedOutput = recompilationSpecProvider.initializeCompilation(spec, recompilationSpec);

        if (Iterables.isEmpty(spec.getSourceFiles()) && spec.getClasses().isEmpty()) {
            LOG.info("None of the classes needs to be compiled! Analysis took {}. ", clock.getElapsed());
            return new RecompilationNotNecessary();
        }

        try {
            WorkResult result = recompilationSpecProvider.decorateResult(recompilationSpec, cleaningCompiler.getCompiler().execute(spec));
            return result.or(WorkResults.didWork(cleanedOutput));
        } finally {
            Collection<String> classesToCompile = recompilationSpec.getClassesToCompile();
            LOG.info("Incremental compilation of {} classes completed in {}.", classesToCompile.size(), clock.getElapsed());
            LOG.debug("Recompiled classes {}", classesToCompile);
        }
    }
}
