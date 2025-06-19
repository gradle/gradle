/*
 * Copyright 2017 the original author or authors.
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
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

/**
 * Immutable, transportable options for configuring Java compilation,
 * which is serialized over to Java compiler daemons.
 */
public class MinimalJavaCompileOptions implements Serializable {

    private final ImmutableList<File> sourcepath;
    private final ImmutableList<String> compilerArgs;
    private final @Nullable String encoding;
    private final @Nullable String bootClasspath;
    private final @Nullable String extensionDirs;
    private final MinimalJavaCompilerDaemonForkOptions forkOptions;
    private final MinimalCompilerDaemonDebugOptions debugOptions;
    private final boolean debug;
    private final boolean deprecation;
    private final boolean failOnError;
    private final boolean listFiles;
    private final boolean verbose;
    private final boolean warnings;
    private final @Nullable File annotationProcessorGeneratedSourcesDirectory;
    private final @Nullable File headerOutputDirectory;
    private final @Nullable String javaModuleVersion;
    private final @Nullable String javaModuleMainClass;
    private final boolean supportsIncrementalCompilationAfterFailure;

    public MinimalJavaCompileOptions(
        ImmutableList<File> sourcepath,
        ImmutableList<String> compilerArgs,
        @Nullable String encoding,
        @Nullable String bootClasspath,
        @Nullable String extensionDirs,
        MinimalJavaCompilerDaemonForkOptions forkOptions,
        MinimalCompilerDaemonDebugOptions debugOptions,
        boolean debug,
        boolean deprecation,
        boolean failOnError,
        boolean listFiles,
        boolean verbose,
        boolean warnings,
        @Nullable File annotationProcessorGeneratedSourcesDirectory,
        @Nullable File headerOutputDirectory,
        @Nullable String javaModuleVersion,
        @Nullable String javaModuleMainClass,
        boolean supportsIncrementalCompilationAfterFailure
    ) {
        this.sourcepath = sourcepath;
        this.compilerArgs = compilerArgs;
        this.encoding = encoding;
        this.bootClasspath = bootClasspath;
        this.extensionDirs = extensionDirs;
        this.forkOptions = forkOptions;
        this.debugOptions = debugOptions;
        this.debug = debug;
        this.deprecation = deprecation;
        this.failOnError = failOnError;
        this.listFiles = listFiles;
        this.verbose = verbose;
        this.warnings = warnings;
        this.annotationProcessorGeneratedSourcesDirectory = annotationProcessorGeneratedSourcesDirectory;
        this.headerOutputDirectory = headerOutputDirectory;
        this.javaModuleVersion = javaModuleVersion;
        this.javaModuleMainClass = javaModuleMainClass;
        this.supportsIncrementalCompilationAfterFailure = supportsIncrementalCompilationAfterFailure;
    }

    @Nullable
    public ImmutableList<File> getSourcepath() {
        return sourcepath;
    }

    public ImmutableList<String> getCompilerArgs() {
        return compilerArgs;
    }

    @Nullable
    public String getEncoding() {
        return encoding;
    }

    @Nullable
    public String getBootClasspath() {
        return bootClasspath;
    }

    @Nullable
    public String getExtensionDirs() {
        return extensionDirs;
    }

    public MinimalJavaCompilerDaemonForkOptions getForkOptions() {
        return forkOptions;
    }

    public MinimalCompilerDaemonDebugOptions getDebugOptions() {
        return debugOptions;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isDeprecation() {
        return deprecation;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public boolean isListFiles() {
        return listFiles;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isWarnings() {
        return warnings;
    }

    @Nullable
    public File getAnnotationProcessorGeneratedSourcesDirectory() {
        return annotationProcessorGeneratedSourcesDirectory;
    }

    @Nullable
    public File getHeaderOutputDirectory() {
        return headerOutputDirectory;
    }

    @Nullable
    public String getJavaModuleVersion() {
        return javaModuleVersion;
    }

    @Nullable
    public String getJavaModuleMainClass() {
        return javaModuleMainClass;
    }

    public boolean supportsIncrementalCompilationAfterFailure() {
        return supportsIncrementalCompilationAfterFailure;
    }

    public MinimalJavaCompileOptions withSourcePath(ImmutableList<File> sourcepath) {
        return new MinimalJavaCompileOptions(
            sourcepath,
            getCompilerArgs(),
            getEncoding(),
            getBootClasspath(),
            getExtensionDirs(),
            getForkOptions(),
            getDebugOptions(),
            isDebug(),
            isDeprecation(),
            isFailOnError(),
            isListFiles(),
            isVerbose(),
            isWarnings(),
            getAnnotationProcessorGeneratedSourcesDirectory(),
            getHeaderOutputDirectory(),
            getJavaModuleVersion(),
            getJavaModuleMainClass(),
            supportsIncrementalCompilationAfterFailure()
        );
    }
}
