/*
 * Copyright 2021 the original author or authors.
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
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Set;

/**
 * Immutable, transportable options for configuring Groovy compilation,
 * which is serialized over to Groovy compiler daemons.
 */
public class MinimalGroovyCompileOptions implements Serializable {

    private final boolean failOnError;
    private final boolean verbose;
    private final boolean listFiles;
    private final String encoding;
    private final boolean fork;
    private final boolean keepStubs;
    private final ImmutableList<String> fileExtensions;
    private final MinimalGroovyCompilerDaemonForkOptions forkOptions;
    private final ImmutableMap<String, Boolean> optimizationOptions;
    private final File stubDir;
    private final @Nullable File configurationScript;
    private final boolean javaAnnotationProcessing;
    private final boolean parameters;
    private final ImmutableSet<String> disabledGlobalASTTransformations;

    public MinimalGroovyCompileOptions(
        boolean failOnError,
        boolean verbose,
        boolean listFiles,
        String encoding,
        boolean fork,
        boolean keepStubs,
        ImmutableList<String> fileExtensions,
        MinimalGroovyCompilerDaemonForkOptions forkOptions,
        ImmutableMap<String, Boolean> optimizationOptions,
        File stubDir,
        @Nullable File configurationScript,
        boolean javaAnnotationProcessing,
        boolean parameters,
        ImmutableSet<String> disabledGlobalASTTransformations
    ) {
        this.failOnError = failOnError;
        this.verbose = verbose;
        this.listFiles = listFiles;
        this.encoding = encoding;
        this.fork = fork;
        this.keepStubs = keepStubs;
        this.fileExtensions = fileExtensions;
        this.forkOptions = forkOptions;
        this.optimizationOptions = optimizationOptions;
        this.stubDir = stubDir;
        this.configurationScript = configurationScript;
        this.javaAnnotationProcessing = javaAnnotationProcessing;
        this.parameters = parameters;
        this.disabledGlobalASTTransformations = disabledGlobalASTTransformations;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isListFiles() {
        return listFiles;
    }

    public String getEncoding() {
        return encoding;
    }

    public boolean isFork() {
        return fork;
    }

    public boolean isKeepStubs() {
        return keepStubs;
    }

    public ImmutableList<String> getFileExtensions() {
        return fileExtensions;
    }

    public MinimalGroovyCompilerDaemonForkOptions getForkOptions() {
        return forkOptions;
    }

    public ImmutableMap<String, Boolean> getOptimizationOptions() {
        return optimizationOptions;
    }

    public File getStubDir() {
        return stubDir;
    }

    @Nullable
    public File getConfigurationScript() {
        return configurationScript;
    }

    public boolean isJavaAnnotationProcessing() {
        return javaAnnotationProcessing;
    }

    public boolean isParameters() {
        return parameters;
    }

    public Set<String> getDisabledGlobalASTTransformations() {
        return disabledGlobalASTTransformations;
    }

}
