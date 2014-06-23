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

import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationNotNecessary;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.util.Clock;

class SelectiveCompiler implements org.gradle.language.base.internal.compile.Compiler<JavaCompileSpec> {
    private static final Logger LOG = Logging.getLogger(SelectiveCompiler.class);
    private final IncrementalTaskInputs inputs;
    private final ClassDependencyInfo classDependencyInfo;
    private final CleaningJavaCompiler cleaningCompiler;
    private final RecompilationSpecProvider recompilationSpecProvider;
    private final IncrementalCompilationInitializer incrementalCompilationInitilizer;

    public SelectiveCompiler(IncrementalTaskInputs inputs, ClassDependencyInfo classDependencyInfo, CleaningJavaCompiler cleaningCompiler,
                             RecompilationSpecProvider recompilationSpecProvider, IncrementalCompilationInitializer compilationInitializer) {
        this.inputs = inputs;
        this.classDependencyInfo = classDependencyInfo;
        this.cleaningCompiler = cleaningCompiler;
        this.recompilationSpecProvider = recompilationSpecProvider;
        this.incrementalCompilationInitilizer = compilationInitializer;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        Clock clock = new Clock();
        RecompilationSpec recompilationSpec = recompilationSpecProvider.provideRecompilationSpec(inputs, classDependencyInfo);

        if (recompilationSpec.isFullRebuildNeeded()) {
            LOG.lifecycle("Incremental java compilation not possible - full rebuild is needed due to a change to: {}. Analysis took {}.", recompilationSpec.getFullRebuildCause().getName(), clock.getTime());
            return cleaningCompiler.execute(spec);
        }

        incrementalCompilationInitilizer.initializeCompilation(spec, recompilationSpec.getClassNames());
        if (spec.getSource().isEmpty()) {
            LOG.lifecycle("None of the classes needs to compiled! Analysis took {}. ", clock.getTime());
            return new RecompilationNotNecessary();
        }

        try {
            //use the original compiler to avoid cleaning up all the files
            return cleaningCompiler.getCompiler().execute(spec);
        } finally {
            LOG.lifecycle("Incremental compilation of {} classes completed in {}.", recompilationSpec.getClassNames().size(), clock.getTime());
        }
    }
}
