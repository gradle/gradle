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
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotWriter;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

class IncrementalCompilationFinalizer implements Compiler<JavaCompileSpec> {

    private final Compiler<JavaCompileSpec> delegate;
    private final JarClasspathSnapshotWriter writer;
    private final ClassSetAnalysisUpdater updater;

    public IncrementalCompilationFinalizer(Compiler<JavaCompileSpec> delegate, JarClasspathSnapshotWriter writer,
                                           ClassSetAnalysisUpdater updater) {
        this.delegate = delegate;
        this.writer = writer;
        this.updater = updater;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        WorkResult out = delegate.execute(spec);

        if (!(out instanceof RecompilationNotNecessary)) {
            //if recompilation was skipped
            //there's no point in updating because we have exactly the same output classes)
            updater.updateAnalysis(spec);
        }

        writer.storeJarSnapshots(spec.getClasspath());

        return out;
    }
}