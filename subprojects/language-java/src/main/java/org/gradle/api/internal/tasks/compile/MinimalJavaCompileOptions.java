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
import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.DebugOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.Factory;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.List;

public class MinimalJavaCompileOptions implements Serializable {
    private List<File> sourcepath;
    private List<String> compilerArgs;
    private String encoding;
    private String bootClasspath;
    private String extensionDirs;
    private ForkOptions forkOptions;
    private DebugOptions debugOptions;
    private boolean debug;
    private boolean deprecation;
    private boolean failOnError;
    private boolean listFiles;
    private boolean verbose;
    private boolean warnings;
    private File annotationProcessorGeneratedSourcesDirectory;

    public MinimalJavaCompileOptions(final CompileOptions compileOptions) {
        FileCollection sourcepath = compileOptions.getSourcepath();
        this.sourcepath = sourcepath == null ? null : ImmutableList.copyOf(sourcepath.getFiles());
        this.compilerArgs = Lists.newArrayList(compileOptions.getCompilerArgs());
        this.encoding = compileOptions.getEncoding();
        this.bootClasspath = DeprecationLogger.whileDisabled(new Factory<String>() {
            @Nullable
            @Override
            @SuppressWarnings("deprecation")
            public String create() {
                return compileOptions.getBootClasspath();
            }
        });
        this.extensionDirs = compileOptions.getExtensionDirs();
        this.forkOptions = compileOptions.getForkOptions();
        this.debugOptions = compileOptions.getDebugOptions();
        this.debug = compileOptions.isDebug();
        this.deprecation = compileOptions.isDeprecation();
        this.failOnError = compileOptions.isFailOnError();
        this.listFiles = compileOptions.isListFiles();
        this.verbose = compileOptions.isVerbose();
        this.warnings = compileOptions.isWarnings();
        this.annotationProcessorGeneratedSourcesDirectory = compileOptions.getAnnotationProcessorGeneratedSourcesDirectory();
    }

    public List<File> getSourcepath() {
        return sourcepath;
    }

    public void setSourcepath(List<File> sourcepath) {
        this.sourcepath = sourcepath;
    }

    public List<String> getCompilerArgs() {
        return compilerArgs;
    }

    public void setCompilerArgs(List<String> compilerArgs) {
        this.compilerArgs = compilerArgs;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getBootClasspath() {
        return bootClasspath;
    }

    public void setBootClasspath(String bootClasspath) {
        this.bootClasspath = bootClasspath;
    }

    public String getExtensionDirs() {
        return extensionDirs;
    }

    public void setExtensionDirs(String extensionDirs) {
        this.extensionDirs = extensionDirs;
    }

    public ForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(ForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    public DebugOptions getDebugOptions() {
        return debugOptions;
    }

    public void setDebugOptions(DebugOptions debugOptions) {
        this.debugOptions = debugOptions;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDeprecation() {
        return deprecation;
    }

    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public boolean isListFiles() {
        return listFiles;
    }

    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isWarnings() {
        return warnings;
    }

    public void setWarnings(boolean warnings) {
        this.warnings = warnings;
    }

    public File getAnnotationProcessorGeneratedSourcesDirectory() {
        return annotationProcessorGeneratedSourcesDirectory;
    }

    public void setAnnotationProcessorGeneratedSourcesDirectory(File annotationProcessorGeneratedSourcesDirectory) {
        this.annotationProcessorGeneratedSourcesDirectory = annotationProcessorGeneratedSourcesDirectory;
    }
}
