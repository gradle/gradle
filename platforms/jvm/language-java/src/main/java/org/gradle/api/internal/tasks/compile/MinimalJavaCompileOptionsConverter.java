/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.ForkOptions;

/**
 * Converts {@link CompileOptions} to {@link MinimalJavaCompileOptions} so they
 * can be serialized and sent to the compiler worker.
 */
public class MinimalJavaCompileOptionsConverter {

    public static MinimalJavaCompileOptions toMinimalJavaCompileOptions(CompileOptions compileOptions) {
        FileCollection sourcepath = compileOptions.getSourcepath();
        FileCollection bootstrapClasspath = compileOptions.getBootstrapClasspath();
        ForkOptions forkOptions = compileOptions.getForkOptions();

        MinimalJavaCompilerDaemonForkOptions minimalForkOptions = new MinimalJavaCompilerDaemonForkOptions(
            forkOptions.getMemoryInitialSize(),
            forkOptions.getMemoryMaximumSize(),
            ImmutableList.copyOf(forkOptions.getAllJvmArgs()),
            forkOptions.getExecutable(),
            forkOptions.getTempDir(),
            forkOptions.getJavaHome()
        );

        return new MinimalJavaCompileOptions(
            sourcepath != null ? ImmutableList.copyOf(sourcepath.getFiles()) : ImmutableList.of(),
            ImmutableList.copyOf(compileOptions.getAllCompilerArgs()),
            compileOptions.getEncoding(),
            bootstrapClasspath != null ? bootstrapClasspath.getAsPath() : null,
            compileOptions.getExtensionDirs(),
            minimalForkOptions,
            new MinimalCompilerDaemonDebugOptions(compileOptions.getDebugOptions().getDebugLevel()),
            compileOptions.isDebug(),
            compileOptions.isDeprecation(),
            compileOptions.isFailOnError(),
            compileOptions.isListFiles(),
            compileOptions.isVerbose(),
            compileOptions.isWarnings(),
            compileOptions.getGeneratedSourceOutputDirectory().getAsFile().getOrNull(),
            compileOptions.getHeaderOutputDirectory().getAsFile().getOrNull(),
            compileOptions.getJavaModuleVersion().getOrNull(),
            compileOptions.getJavaModuleMainClass().getOrNull(),
            compileOptions.getIncrementalAfterFailure().getOrElse(false)
        );
    }

}
