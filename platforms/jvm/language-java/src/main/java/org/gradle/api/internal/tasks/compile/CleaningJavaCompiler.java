/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.compile;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Deletes stale classes before invoking the actual compiler.
 */
public class CleaningJavaCompiler<T extends JavaCompileSpec> implements Compiler<T> {
    private final Compiler<T> compiler;
    private final TaskOutputsInternal taskOutputs;
    private final Deleter deleter;

    public CleaningJavaCompiler(Compiler<T> compiler, TaskOutputsInternal taskOutputs, Deleter deleter) {
        this.compiler = compiler;
        this.taskOutputs = taskOutputs;
        this.deleter = deleter;
    }

    @Override
    public WorkResult execute(T spec) {
        ImmutableSet.Builder<File> outputDirs = ImmutableSet.builderWithExpectedSize(3);
        MinimalJavaCompileOptions compileOptions = spec.getCompileOptions();
        addDirectoryIfNotNull(outputDirs, spec.getDestinationDir());
        addDirectoryIfNotNull(outputDirs, compileOptions.getAnnotationProcessorGeneratedSourcesDirectory());
        addDirectoryIfNotNull(outputDirs, compileOptions.getHeaderOutputDirectory());
        boolean cleanedOutputs = StaleOutputCleaner.cleanOutputs(deleter, taskOutputs.getPreviousOutputFiles(), outputDirs.build());

        Compiler<? super T> compiler = getCompiler();
        return compiler.execute(spec)
            .or(WorkResults.didWork(cleanedOutputs));
    }

    private void addDirectoryIfNotNull(ImmutableSet.Builder<File> outputDirs, @Nullable File dir) {
        if (dir != null) {
            outputDirs.add(dir);
        }
    }

    public Compiler<T> getCompiler() {
        return compiler;
    }
}
