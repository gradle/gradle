/*
 * Copyright 2007-2008 the original author or authors.
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
import com.google.common.collect.Maps;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Compilation options to be passed to the Groovy compiler.
 */
public class GroovyCompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;
    private static final ImmutableSet<String> EXCLUDE_FROM_ANT_PROPERTIES =
            ImmutableSet.of("forkOptions", "optimizationOptions", "useAnt", "stubDir", "keepStubs", "fileExtensions");

    private boolean failOnError = true;

    private boolean verbose;

    private boolean listFiles;

    private String encoding = "UTF-8";

    private boolean fork = true;

    private boolean keepStubs;

    private List<String> fileExtensions = ImmutableList.of("java", "groovy");

    private GroovyForkOptions forkOptions = new GroovyForkOptions();

    private Map<String, Boolean> optimizationOptions = Maps.newHashMap();

    private boolean stacktrace;

    private boolean useAnt;

    private boolean includeJavaRuntime;

    private File stubDir;

    /**
     * Tells whether the compilation task should fail if compile errors occurred. Defaults to {@code true}.
     */
    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * Sets whether the compilation task should fail if compile errors occurred. Defaults to {@code true}.
     */
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Tells whether to turn on verbose output. Defaults to {@code false}.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether to turn on verbose output. Defaults to {@code false}.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Tells whether to print which source files are to be compiled. Defaults to {@code false}.
     */
    public boolean isListFiles() {
        return listFiles;
    }

    /**
     * Sets whether to print which source files are to be compiled. Defaults to {@code false}.
     */
    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    /**
     * Tells the source encoding. Defaults to {@code UTF-8}.
     */
    @Input
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the source encoding. Defaults to {@code UTF-8}.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Tells whether to run the Groovy compiler in a separate process. Defaults to {@code true}.
     */
    public boolean isFork() {
        return fork;
    }

    /**
     * Sets whether to run the Groovy compiler in a separate process. Defaults to {@code true}.
     */
    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Returns options for running the Groovy compiler in a separate process. These options only take effect
     * if {@code fork} is set to {@code true}.
     */
    public GroovyForkOptions getForkOptions() {
        return forkOptions;
    }

    /**
     * Sets options for running the Groovy compiler in a separate process. These options only take effect
     * if {@code fork} is set to {@code true}.
     */
    public void setForkOptions(GroovyForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    /**
     * Returns optimization options for the Groovy compiler. Allowed values for an option are {@code true} and {@code false}.
     * Only takes effect when compiling against Groovy 1.8 or higher.
     *
     * <p>Known options are:
     *
     * <dl>
     *     <dt>indy
     *     <dd>Use the invokedynamic bytecode instruction. Requires JDK7 or higher and Groovy 2.0 or higher. Disabled by default.
     *     <dt>int
     *     <dd>Optimize operations on primitive types (e.g. integers). Enabled by default.
     *     <dt>all
     *     <dd>Enable or disable all optimizations. Note that some optimizations might be mutually exclusive.
     * </dl>
     */
    public Map<String, Boolean> getOptimizationOptions() {
        return optimizationOptions;
    }

    /**
     * Sets optimization options for the Groovy compiler. Allowed values for an option are {@code true} and {@code false}.
     * Only takes effect when compiling against Groovy 1.8 or higher.
     */
    public void setOptimizationOptions(Map<String, Boolean> optimizationOptions) {
        this.optimizationOptions = optimizationOptions;
    }

    /**
     * Tells whether to print a stack trace when the compiler hits a problem (like a compile error).
     * Defaults to {@code false}. Only used when {@link #isUseAnt()} is {@code true}.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public boolean isStacktrace() {
        DeprecationLogger.nagUserOfDiscontinuedProperty("GroovyCompileOptions.stacktrace", "There is no replacement for this property.");
        return stacktrace;
    }

    /**
     * Sets whether to print a stack trace when the compiler hits a problem (like a compile error).
     * Defaults to {@code false}. Only used when {@link #isUseAnt()} is {@code true}.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public void setStacktrace(boolean stacktrace) {
        DeprecationLogger.nagUserOfDiscontinuedProperty("GroovyCompileOptions.stacktrace", "This property has no replacement.");
        this.stacktrace = stacktrace;
    }

    /**
     * Tells whether the groovyc Ant task should be used over Gradle's own Groovy compiler integration.
     * Defaults to {@code false}.
     *
     * @deprecated No replacement
     */
    @Input
    @Deprecated
    public boolean isUseAnt() {
        DeprecationLogger.nagUserOfDiscontinuedProperty("GroovyCompileOptions.useAnt", "There is no replacement for this property.");
        return useAnt;
    }

    /**
     * Sets whether the groovyc Ant task should be used over Gradle's own Groovy compiler integration.
     * Defaults to {@code false}.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public void setUseAnt(boolean useAnt) {
        DeprecationLogger.nagUserOfDiscontinuedProperty("GroovyCompileOptions.useAnt", "There is no replacement for this property.");
        this.useAnt = useAnt;
    }

    /**
     * Tells whether the Java runtime should be put on the compile class path. Only takes effect if
     * {@code useAnt} is {@code true}. Defaults to {@code false}.
     *
     * @deprecated No replacement
     */
    @Input
    @Deprecated
    public boolean isIncludeJavaRuntime() {
        DeprecationLogger.nagUserOfDiscontinuedProperty("GroovyCompileOptions.includeJavaRuntime", "There is no replacement for this property.");
        return includeJavaRuntime;
    }

    /**
     * Sets whether the Java runtime should be put on the compile class path. Only takes effect if
     * {@code useAnt} is {@code true}. Defaults to {@code false}.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public void setIncludeJavaRuntime(boolean includeJavaRuntime) {
        DeprecationLogger.nagUserOfDiscontinuedProperty("GroovyCompileOptions.includeJavaRuntime", "There is no replacement for this property.");
        this.includeJavaRuntime = includeJavaRuntime;
    }

    /**
     * Returns the directory where Java stubs for Groovy classes will be stored during Java/Groovy joint
     * compilation. Defaults to {@code null}, in which case a temporary directory will be used.
     */
    public File getStubDir() {
        return stubDir;
    }

    /**
     * Sets the directory where Java stubs for Groovy classes will be stored during Java/Groovy joint
     * compilation. Defaults to {@code null}, in which case a temporary directory will be used.
     */
    public void setStubDir(File stubDir) {
        this.stubDir = stubDir;
    }

    /**
     * Returns the list of acceptable source file extensions. Only takes effect when compiling against
     * Groovy 1.7 or higher. Defaults to {@code ImmutableList.of("java", "groovy")}.
     */
    @Input
    @Incubating
    public List<String> getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Sets the list of acceptable source file extensions. Only takes effect when compiling against
     * Groovy 1.7 or higher. Defaults to {@code ImmutableList.of("java", "groovy")}.
     */
    @Incubating
    public void setFileExtensions(List<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    /**
     * Tells whether Java stubs for Groovy classes generated during Java/Groovy joint compilation
     * should be kept after compilation has completed. Useful for joint compilation debugging purposes.
     * Defaults to {@code false}.
     */
    public boolean isKeepStubs() {
        return keepStubs;
    }

    /**
     * Sets whether Java stubs for Groovy classes generated during Java/Groovy joint compilation
     * should be kept after compilation has completed. Useful for joint compilation debugging purposes.
     * Defaults to {@code false}.
     */
    public void setKeepStubs(boolean keepStubs) {
        this.keepStubs = keepStubs;
    }

    /**
     * Convenience method to set {@link GroovyForkOptions} with named parameter syntax.
     * Calling this method will set {@code fork} to {@code true}.
     */
    public GroovyCompileOptions fork(Map forkArgs) {
        fork = true;
        forkOptions.define(forkArgs);
        return this;
    }

    @Override
    protected boolean excludeFromAntProperties(String fieldName) {
        return EXCLUDE_FROM_ANT_PROPERTIES.contains(fieldName);
    }

    /**
     * Internal method.
     */
    public Map<String, Object> optionMap() {
        Map<String, Object> map = super.optionMap();
        map.putAll(forkOptions.optionMap());
        if (optimizationOptions.containsKey("indy")) {
            map.put("indy", optimizationOptions.get("indy"));
        }
        return map;
    }
}
