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
import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Converts {@link CompileOptions} to {@link MinimalJavaCompileOptions} so they
 * can be serialized and sent to the compiler worker.
 */
public class MinimalJavaCompileOptionsConverter {

    public static MinimalJavaCompileOptions toMinimalJavaCompileOptions(CompileOptions compileOptions) {
        ForkOptions forkOptions = compileOptions.getForkOptions();
        FileCollection sourcepath = compileOptions.getSourcepath();
        FileCollection bootstrapClasspath = compileOptions.getBootstrapClasspath();

        MinimalJavaCompileOptions result = new MinimalJavaCompileOptions();

        // TODO: MinimalJavaCompileOptions should be immutable.
        result.setSourcepath(sourcepath == null ? null : ImmutableList.copyOf(sourcepath.getFiles()));
        result.setCompilerArgs(Lists.newArrayList(compileOptions.getAllCompilerArgs()));
        result.setEncoding(compileOptions.getEncoding());
        result.setBootClasspath(bootstrapClasspath == null ? null : bootstrapClasspath.getAsPath());
        result.setExtensionDirs(compileOptions.getExtensionDirs());
        result.setForkOptions(new MinimalJavaCompilerDaemonForkOptions(
            forkOptions.getMemoryInitialSize(),
            forkOptions.getMemoryMaximumSize(),
            forkOptions.getAllJvmArgs(),
            forkOptions.getExecutable(),
            forkOptions.getTempDir(),
            getJavaHomeFor(forkOptions)
        ));
        result.setDebugOptions(new MinimalCompilerDaemonDebugOptions(compileOptions.getDebugOptions().getDebugLevel()));
        result.setDebug(compileOptions.isDebug());
        result.setDeprecation(compileOptions.isDeprecation());
        result.setFailOnError(compileOptions.isFailOnError());
        result.setListFiles(compileOptions.isListFiles());
        result.setVerbose(compileOptions.isVerbose());
        result.setWarnings(compileOptions.isWarnings());
        result.setAnnotationProcessorGeneratedSourcesDirectory(compileOptions.getGeneratedSourceOutputDirectory().getAsFile().getOrNull());
        result.setHeaderOutputDirectory(compileOptions.getHeaderOutputDirectory().getAsFile().getOrNull());
        result.setJavaModuleVersion(compileOptions.getJavaModuleVersion().getOrNull());
        result.setJavaModuleMainClass(compileOptions.getJavaModuleMainClass().getOrNull());
        result.setSupportsIncrementalCompilationAfterFailure(compileOptions.getIncrementalAfterFailure().getOrElse(false));

        return result;
    }

    @SuppressWarnings("deprecation")
    private static @Nullable File getJavaHomeFor(ForkOptions forkOptions) {
        return DeprecationLogger.whileDisabled(forkOptions::getJavaHome);
    }

}
