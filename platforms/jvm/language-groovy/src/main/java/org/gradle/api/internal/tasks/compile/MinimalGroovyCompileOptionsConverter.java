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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.tasks.compile.GroovyCompileOptions;
import org.gradle.api.tasks.compile.GroovyForkOptions;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Converts {@link GroovyCompileOptions} to {@link MinimalGroovyCompileOptions} so they
 * can be serialized and sent to the compiler worker.
 */
public class MinimalGroovyCompileOptionsConverter {

    public static MinimalGroovyCompileOptions toMinimalGroovyCompileOptions(GroovyCompileOptions compileOptions) {
        GroovyForkOptions forkOptions = compileOptions.getForkOptions();

        MinimalGroovyCompilerDaemonForkOptions minimalForkOptions = new MinimalGroovyCompilerDaemonForkOptions(
            forkOptions.getMemoryInitialSize(),
            forkOptions.getMemoryMaximumSize(),
            ImmutableList.copyOf(forkOptions.getAllJvmArgs())
        );

        return new MinimalGroovyCompileOptions(
            compileOptions.isFailOnError(),
            compileOptions.isVerbose(),
            compileOptions.isListFiles(),
            compileOptions.getEncoding(),
            compileOptions.isFork(),
            compileOptions.isKeepStubs(),
            ImmutableList.copyOf(compileOptions.getFileExtensions()),
            minimalForkOptions,
            asImmutableMap(compileOptions.getOptimizationOptions()),
            compileOptions.getStubDir(),
            compileOptions.getConfigurationScript(),
            compileOptions.isJavaAnnotationProcessing(),
            compileOptions.isParameters(),
            ImmutableSet.copyOf(compileOptions.getDisabledGlobalASTTransformations().get())
        );
    }

    private static <K, V> ImmutableMap<K, V> asImmutableMap(@Nullable Map<K, V> map) {
        if (map == null) {
            return ImmutableMap.of();
        }

        return ImmutableMap.copyOf(map);
    }

}
