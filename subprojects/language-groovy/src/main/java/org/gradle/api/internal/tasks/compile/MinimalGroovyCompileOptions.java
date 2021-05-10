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
import com.google.common.collect.Maps;
import org.gradle.api.tasks.compile.GroovyCompileOptions;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class MinimalGroovyCompileOptions implements Serializable {
    private boolean failOnError;
    private boolean verbose;
    private boolean listFiles;
    private String encoding;
    private boolean fork = true;
    private boolean keepStubs;
    private List<String> fileExtensions;
    private MinimalGroovyCompilerDaemonForkOptions forkOptions;
    private Map<String, Boolean> optimizationOptions;
    private File stubDir;
    private File configurationScript;
    private boolean javaAnnotationProcessing;
    private boolean parameters;

    public MinimalGroovyCompileOptions(GroovyCompileOptions compileOptions) {
        this.failOnError = compileOptions.isFailOnError();
        this.verbose = compileOptions.isVerbose();
        this.listFiles = compileOptions.isListFiles();
        this.encoding = compileOptions.getEncoding();
        this.fork = compileOptions.isFork();
        this.keepStubs = compileOptions.isKeepStubs();
        this.fileExtensions = ImmutableList.copyOf(compileOptions.getFileExtensions());
        this.forkOptions = new MinimalGroovyCompilerDaemonForkOptions(compileOptions.getForkOptions());
        this.optimizationOptions = Maps.newHashMap(compileOptions.getOptimizationOptions());
        this.stubDir = compileOptions.getStubDir();
        this.configurationScript = compileOptions.getConfigurationScript();
        this.javaAnnotationProcessing = compileOptions.isJavaAnnotationProcessing();
        this.parameters = compileOptions.isParameters();
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isListFiles() {
        return listFiles;
    }

    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    public boolean isKeepStubs() {
        return keepStubs;
    }

    public void setKeepStubs(boolean keepStubs) {
        this.keepStubs = keepStubs;
    }

    public List<String> getFileExtensions() {
        return fileExtensions;
    }

    public void setFileExtensions(List<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    public MinimalGroovyCompilerDaemonForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(MinimalGroovyCompilerDaemonForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    @Nullable
    public Map<String, Boolean> getOptimizationOptions() {
        return optimizationOptions;
    }

    public void setOptimizationOptions(@Nullable Map<String, Boolean> optimizationOptions) {
        this.optimizationOptions = optimizationOptions;
    }

    public File getStubDir() {
        return stubDir;
    }

    public void setStubDir(File stubDir) {
        this.stubDir = stubDir;
    }

    @Nullable
    public File getConfigurationScript() {
        return configurationScript;
    }

    public void setConfigurationScript(@Nullable File configurationScript) {
        this.configurationScript = configurationScript;
    }

    public boolean isJavaAnnotationProcessing() {
        return javaAnnotationProcessing;
    }

    public void setJavaAnnotationProcessing(boolean javaAnnotationProcessing) {
        this.javaAnnotationProcessing = javaAnnotationProcessing;
    }

    public boolean isParameters() {
        return parameters;
    }

    public void setParameters(boolean parameters) {
        this.parameters = parameters;
    }
}
