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
import com.google.common.collect.Maps;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Compilation options to be passed to the Groovy compiler.
 */
public class GroovyCompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private boolean failOnError = true;

    private boolean verbose;

    private boolean listFiles;

    private String encoding = "UTF-8";

    private boolean fork = true;

    private boolean keepStubs;

    private List<String> fileExtensions = ImmutableList.of("java", "groovy");

    private GroovyForkOptions forkOptions = new GroovyForkOptions();

    private Map<String, Boolean> optimizationOptions = Maps.newHashMap();

    private File stubDir;

    private File configurationScript;

    private boolean javaAnnotationProcessing;

    private boolean parameters;

    /**
     * Tells whether the compilation task should fail if compile errors occurred. Defaults to {@code true}.
     */
    @Input
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
    @Console
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
    @Console
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
    @Input
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
     * A Groovy script file that configures the compiler, allowing extensive control over how the code is compiled.
     * <p>
     * The script is executed as Groovy code, with the following context:
     * </p>
     * <ul>
     * <li>The instance of <a href="https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/CompilerConfiguration.html">CompilerConfiguration</a> available as the {@code configuration} variable.</li>
     * <li>All static members of <a href="https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/customizers/builder/CompilerCustomizationBuilder.html">CompilerCustomizationBuilder</a> pre imported.</li>
     * </ul>
     * <p>
     * This facilitates the following pattern:
     * </p>
     * <pre>
     * withConfig(configuration) {
     *   // use compiler configuration DSL here
     * }
     * </pre>
     * <p>
     * For example, to activate type checking for all Groovy classes…
     * </p>
     * <pre>
     * import groovy.transform.TypeChecked
     *
     * withConfig(configuration) {
     *     ast(TypeChecked)
     * }
     * </pre>
     * <p>
     * Please see <a href="https://docs.groovy-lang.org/latest/html/documentation/#compilation-customizers">the Groovy compiler customization builder documentation</a>
     * for more information about the compiler configuration DSL.
     * </p>
     * <p>
     * <b>This feature is only available if compiling with Groovy 2.1 or later.</b>
     * </p>
     * @see <a href="https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/CompilerConfiguration.html">CompilerConfiguration</a>
     * @see <a href="https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/customizers/builder/CompilerCustomizationBuilder.html">CompilerCustomizationBuilder</a>
     */
    @Nullable
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public File getConfigurationScript() {
        return configurationScript;
    }

    /**
     * Sets the path to the groovy configuration file.
     *
     * @see #getConfigurationScript()
     */
    public void setConfigurationScript(@Nullable File configurationFile) {
        this.configurationScript = configurationFile;
    }

    /**
     * Whether the Groovy code should be subject to Java annotation processing.
     * <p>
     * Annotation processing of Groovy code works by having annotation processors visit the Java stubs generated by the
     * Groovy compiler in order to support joint compilation of Groovy and Java source.
     * <p>
     * When set to {@code true}, stubs will be unconditionally generated for all Groovy sources, and Java annotations processors will be executed on those stubs.
     * <p>
     * When this option is set to {@code false} (the default), Groovy code will not be subject to annotation processing, but any joint compiled Java code will be.
     * If the compiler argument {@code "-proc:none"} was specified as part of the Java compile options, the value of this flag will be ignored.
     * No annotation processing will be performed regardless, on Java or Groovy source.
     */
    @Input
    public boolean isJavaAnnotationProcessing() {
        return javaAnnotationProcessing;
    }

    /**
     * Sets whether Java annotation processors should process annotations on stubs.
     *
     * Defaults to {@code false}.
     */
    public void setJavaAnnotationProcessing(boolean javaAnnotationProcessing) {
        this.javaAnnotationProcessing = javaAnnotationProcessing;
    }

    /**
     * Whether the Groovy compiler generate metadata for reflection on method parameter names on JDK 8 and above.
     *
     * @since 6.1
     */
    @Input
    public boolean isParameters() {
        return parameters;
    }

    /**
     * Sets whether metadata for reflection on method parameter names should be generated.
     * Defaults to {@code false}
     *
     * @since 6.1
     */
    public void setParameters(boolean parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns options for running the Groovy compiler in a separate process. These options only take effect
     * if {@code fork} is set to {@code true}.
     */
    @Nested
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
    @Nullable @Optional @Input
    public Map<String, Boolean> getOptimizationOptions() {
        return optimizationOptions;
    }

    /**
     * Sets optimization options for the Groovy compiler. Allowed values for an option are {@code true} and {@code false}.
     * Only takes effect when compiling against Groovy 1.8 or higher.
     */
    public void setOptimizationOptions(@Nullable Map<String, Boolean> optimizationOptions) {
        this.optimizationOptions = optimizationOptions;
    }

    /**
     * Returns the directory where Java stubs for Groovy classes will be stored during Java/Groovy joint
     * compilation. Defaults to {@code null}, in which case a temporary directory will be used.
     */
    @Internal
    // TOOD:LPTR Should be just a relative path
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
    public List<String> getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Sets the list of acceptable source file extensions. Only takes effect when compiling against
     * Groovy 1.7 or higher. Defaults to {@code ImmutableList.of("java", "groovy")}.
     */
    public void setFileExtensions(List<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    /**
     * Tells whether Java stubs for Groovy classes generated during Java/Groovy joint compilation
     * should be kept after compilation has completed. Useful for joint compilation debugging purposes.
     * Defaults to {@code false}.
     */
    @Input
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
    public GroovyCompileOptions fork(Map<String, Object> forkArgs) {
        fork = true;
        forkOptions.define(forkArgs);
        return this;
    }
}
