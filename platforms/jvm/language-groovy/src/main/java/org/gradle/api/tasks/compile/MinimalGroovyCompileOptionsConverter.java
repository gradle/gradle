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

package org.gradle.api.tasks.compile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.compile.MinimalGroovyCompileOptions;
import org.gradle.api.internal.tasks.compile.MinimalGroovyCompilerDaemonForkOptions;

import java.util.HashMap;

/**
 * Converts {@link GroovyCompileOptions} to {@link MinimalGroovyCompileOptions} so they
 * can be serialized and sent to the compiler worker.
 */
public class MinimalGroovyCompileOptionsConverter {

    public static MinimalGroovyCompileOptions toMinimalGroovyCompileOptions(GroovyCompileOptions compileOptions) {
        GroovyForkOptions forkOptions = compileOptions.getForkOptions();

        MinimalGroovyCompileOptions result = new MinimalGroovyCompileOptions();

        // TODO: MinimalGroovyCompileOptions should be immutable.
        result.setFailOnError(compileOptions.isFailOnError());
        result.setVerbose(compileOptions.isVerbose());
        result.setListFiles(compileOptions.isListFiles());
        result.setEncoding(compileOptions.getEncoding());
        result.setFork(compileOptions.isFork());
        result.setKeepStubs(compileOptions.isKeepStubs());
        result.setFileExtensions(ImmutableList.copyOf(compileOptions.getFileExtensions()));
        result.setForkOptions(new MinimalGroovyCompilerDaemonForkOptions(
            forkOptions.getMemoryInitialSize(),
            forkOptions.getMemoryMaximumSize(),
            forkOptions.getAllJvmArgs()
        ));
        result.setOptimizationOptions(new HashMap<>(compileOptions.getOptimizationOptions()));
        result.setStubDir(compileOptions.getStubDir());
        result.setConfigurationScript(compileOptions.getConfigurationScript());
        result.setJavaAnnotationProcessing(compileOptions.isJavaAnnotationProcessing());
        result.setParameters(compileOptions.isParameters());
        result.setDisabledGlobalASTTransformations(ImmutableSet.copyOf(compileOptions.getDisabledGlobalASTTransformations().get()));

        return result;
    }
}
