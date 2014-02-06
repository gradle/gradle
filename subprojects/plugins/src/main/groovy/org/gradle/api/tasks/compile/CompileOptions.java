/*
 * Copyright 2007 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.SingleMessageLogger;

import java.util.List;
import java.util.Map;

/**
 * Main options for Java compilation.
 */
public class CompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private static final ImmutableSet<String> EXCLUDE_FROM_ANT_PROPERTIES =
            ImmutableSet.of("debugOptions", "forkOptions", "compilerArgs", "dependOptions", "useDepend", "useAnt", "incremental");

    private boolean failOnError = true;

    private boolean verbose;

    private boolean listFiles;

    private boolean deprecation;

    private boolean warnings = true;

    private String encoding;

    private boolean optimize;

    private boolean debug = true;

    private DebugOptions debugOptions = new DebugOptions();

    private boolean fork;

    private ForkOptions forkOptions = new ForkOptions();

    private boolean useDepend;

    private DependOptions dependOptions = new DependOptions();

    private String compiler;

    private boolean includeJavaRuntime;

    private String bootClasspath;

    private String extensionDirs;

    private List<String> compilerArgs = Lists.newArrayList();

    private boolean useAnt;
    private boolean incremental;

    /**
     * Tells whether to fail the build when compilation fails. Defaults to {@code true}.
     */
    @Input
    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * Deprecated.
     *
     * @deprecated use {@link #isFailOnError()}
     */
    @Deprecated
    public boolean getFailOnError() {
        DeprecationLogger.nagUserOfDiscontinuedProperty("CompileOptions.getFailOnError()", "CompileOptions.isFailOnError()");
        return failOnError;
    }

    /**
     * Sets whether to fail the build when compilation fails. Defaults to {@code true}.
     */
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Tells whether to produce verbose output. Defaults to {@code false}.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether to produce verbose output. Defaults to {@code false}.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Tells whether to log the files to be compiled. Defaults to {@code false}.
     */
    public boolean isListFiles() {
        return listFiles;
    }

    /**
     * Sets whether to log the files to be compiled. Defaults to {@code false}.
     */
    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    /**
     * Tells whether to log details of usage of deprecated members or classes. Defaults to {@code false}.
     */
    public boolean isDeprecation() {
        return deprecation;
    }

    /**
     * Sets whether to log details of usage of deprecated members or classes. Defaults to {@code false}.
     */
    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    /**
     * Tells whether to log warning messages. The default is {@code true}.
     */
    public boolean isWarnings() {
        return warnings;
    }

    /**
     * Sets whether to log warning messages. The default is {@code true}.
     */
    public void setWarnings(boolean warnings) {
        this.warnings = warnings;
    }

    /**
     * Returns the character encoding to be used when reading source files. Defaults to {@code null}, in which
     * case the platform default encoding will be used.
     */
    @Input
    @Optional
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the character encoding to be used when reading source files. Defaults to {@code null}, in which
     * case the platform default encoding will be used.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Tells whether to produce optimized byte code. Only takes effect if {@code useAnt} is {@code true}.
     * Note that this flag is ignored by Sun's javac starting with JDK 1.3.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public boolean isOptimize() {
        return optimize;
    }

    /**
     * Tells whether to produce optimized byte code. Only takes effect if {@code useAnt} is {@code true}.
     * Note that this flag is ignored by Sun's javac starting with JDK 1.3.
     *
     * @deprecated No replacement
     */
    @Input
    @Deprecated
    public boolean getOptimize() {
        return optimize;
    }

    /**
     * Sets whether to produce optimized byte code. Only takes effect if {@code useAnt} is {@code true}.
     * Note that this flag is ignored by Sun's javac starting with JDK 1.3.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public void setOptimize(boolean optimize) {
        DeprecationLogger.nagUserOfDiscontinuedProperty("CompileOptions.optimize", "There is no replacement for this property.");
        this.optimize = optimize;
    }

    /**
     * Tells whether to include debugging information in the generated class files. Defaults
     * to {@code true}. See {@link DebugOptions#getDebugLevel()} for which debugging information will be generated.
     */
    @Input
    public boolean isDebug() {
        return debug;
    }

    /**
     * Deprecated.
     *
     * @deprecated use {@link #isDebug()}
     */
    @Deprecated
    public boolean getDebug() {
        DeprecationLogger.nagUserOfReplacedMethod("CompileOptions.getDebug()", "CompileOptions.isDebug()");
        return debug;
    }

    /**
     * Sets whether to include debugging information in the generated class files. Defaults
     * to {@code true}. See {@link DebugOptions#getDebugLevel()} for which debugging information will be generated.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Returns options for generating debugging information.
     */
    @Nested
    public DebugOptions getDebugOptions() {
        return debugOptions;
    }

    /**
     * Sets options for generating debugging information.
     */
    public void setDebugOptions(DebugOptions debugOptions) {
        this.debugOptions = debugOptions;
    }

    /**
     * Tells whether to run the compiler in its own process. Note that this does
     * not necessarily mean that a new process will be created for each compile task.
     * Defaults to {@code false}.
     */
    public boolean isFork() {
        return fork;
    }

    /**
     * Sets whether to run the compiler in its own process. Note that this does
     * not necessarily mean that a new process will be created for each compile task.
     * Defaults to {@code false}.
     */
    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Returns options for running the compiler in a child process.
     */
    @Nested
    public ForkOptions getForkOptions() {
        return forkOptions;
    }

    /**
     * Sets options for running the compiler in a child process.
     */
    public void setForkOptions(ForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    /**
     * Tells whether to use the Ant {@code <depend>} task.
     * Only takes effect if {@code useAnt} is {@code true}. Defaults to
     * {@code false}.
     */
    public boolean isUseDepend() {
        return useDepend;
    }

    /**
     * Sets whether to use the Ant {@code <depend>} task.
     * Only takes effect if {@code useAnt} is {@code true}. Defaults to
     * {@code false}.
     */
    public void setUseDepend(boolean useDepend) {
        this.useDepend = useDepend;
    }

    /**
     * Returns options for using the Ant {@code <depend>} task.
     */
    public DependOptions getDependOptions() {
        return dependOptions;
    }

    /**
     * Sets options for using the Ant {@code <depend>} task.
     */
    public void setDependOptions(DependOptions dependOptions) {
        this.dependOptions = dependOptions;
    }

    /**
     * Returns the compiler to be used. Only takes effect if {@code useAnt} is {@code true}.
     *
     * @deprecated use {@code CompileOptions.forkOptions.executable} instead
     */
    @Deprecated
    @Input @Optional
    public String getCompiler() {
        return compiler;
    }

    /**
     * Sets the compiler to be used. Only takes effect if {@code useAnt} is {@code true}.
     *
     * @deprecated use {@code CompileOptions.forkOptions.executable instead}
     */
    @Deprecated
    public void setCompiler(String compiler) {
        DeprecationLogger.nagUserOfDiscontinuedProperty("CompileOptions.compiler", "To use an alternative compiler, "
                + "set 'CompileOptions.fork' to 'true', and 'CompileOptions.forkOptions.executable' to the path of the compiler executable.");
        this.compiler = compiler;
    }

    /**
     * Tells whether the Java runtime should be put on the compile class path. Only takes effect if
     * {@code useAnt} is {@code true}. Defaults to {@code false}.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public boolean isIncludeJavaRuntime() {
        return includeJavaRuntime;
    }

    /**
     * Tells whether the Java runtime should be put on the compile class path. Only takes effect if
     * {@code useAnt} is {@code true}. Defaults to {@code false}.
     *
     * @deprecated No replacement
     */
    @Input
    @Deprecated
    public boolean getIncludeJavaRuntime() {
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
        DeprecationLogger.nagUserOfDiscontinuedProperty("CompileOptions.includeJavaRuntime", "There is no replacement for this property.");
        this.includeJavaRuntime = includeJavaRuntime;
    }

    /**
     * Returns the bootstrap classpath to be used for the compiler process.
     * Only takes effect if {@code fork} is {@code true}. Defaults to {@code null}.
     */
    @Input
    @Optional
    public String getBootClasspath() {
        return bootClasspath;
    }

    /**
     * Sets the bootstrap classpath to be used for the compiler process.
     * Only takes effect if {@code fork} is {@code true}. Defaults to {@code null}.
     */
    public void setBootClasspath(String bootClasspath) {
        this.bootClasspath = bootClasspath;
    }

    /**
     * Returns the extension dirs to be used for the compiler process.
     * Only takes effect if {@code fork} is {@code true}. Defaults to {@code null}.
     */
    @Input 
    @Optional
    public String getExtensionDirs() {
        return extensionDirs;
    }

    /**
     * Sets the extension dirs to be used for the compiler process.
     * Only takes effect if {@code fork} is {@code true}. Defaults to {@code null}.
     */
    public void setExtensionDirs(String extensionDirs) {
        this.extensionDirs = extensionDirs;
    }

    /**
     * Returns any additional arguments to be passed to the compiler.
     * Defaults to the empty list.
     */
    @Input
    public List<String> getCompilerArgs() {
        return compilerArgs;
    }

    /**
     * Sets any additional arguments to be passed to the compiler.
     * Defaults to the empty list.
     */
    public void setCompilerArgs(List<String> compilerArgs) {
        this.compilerArgs = compilerArgs;
    }

    /**
     * Tells whether to use the Ant javac task over Gradle's own Java compiler integration.
     * Defaults to {@code false}.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public boolean isUseAnt() {
        return useAnt;
    }

    /**
     * Sets whether to use the Ant javac task over Gradle's own Java compiler integration.
     * Defaults to {@code false}.
     *
     * @deprecated No replacement
     */
    @Deprecated
    public void setUseAnt(boolean useAnt) {
        DeprecationLogger.nagUserOfDiscontinuedProperty("CompileOptions.useAnt", "There is no replacement for this property.");
        this.useAnt = useAnt;
    }

    /**
     * Convenience method to set {@link ForkOptions} with named parameter syntax.
     * Calling this method will set {@code fork} to {@code true}.
     */
    public CompileOptions fork(Map<String, Object> forkArgs) {
        fork = true;
        forkOptions.define(forkArgs);
        return this;
    }

    /**
     * Convenience method to set {@link DebugOptions} with named parameter syntax.
     * Calling this method will set {@code debug} to {@code true}.
     */
    public CompileOptions debug(Map<String, Object> debugArgs) {
        debug = true;
        debugOptions.define(debugArgs);
        return this;
    }

    /**
     * Convenience method to set {@link DependOptions} with named parameter syntax.
     * Calling this method will set {@code useDepend} to {@code true}.
     */
    public CompileOptions depend(Map<String, Object> dependArgs) {
        useDepend = true;
        dependOptions.define(dependArgs);
        return this;
    }

    @Incubating
    /**
     * Configure the java compilation to be incremental (e.g. compiles only those java classes that were changed or that are dependencies to the changed classes).
     * The feature is incubating and does not yet satisfies all compilation scenarios.
     */
    public CompileOptions setIncremental(boolean incremental) {
        SingleMessageLogger.incubatingFeatureUsed("Incremental java compilation");
        this.incremental = incremental;
        return this;
    }

    /**
     * Internal method.
     */
    public Map<String, Object> optionMap() {
        Map<String, Object> map = super.optionMap();
        map.putAll(debugOptions.optionMap());
        map.putAll(forkOptions.optionMap());
        return map;
    }

    @Override
    protected boolean excludeFromAntProperties(String fieldName) {
        return EXCLUDE_FROM_ANT_PROPERTIES.contains(fieldName);
    }

    @Override
    protected String getAntPropertyName(String fieldName) {
        if (fieldName.equals("warnings")) {
            return "nowarn";
        }
        if (fieldName.equals("extensionDirs")) {
            return "extdirs";
        }
        return fieldName;
    }

    @Override
    protected Object getAntPropertyValue(String fieldName, Object value) {
        if (fieldName.equals("warnings")) {
            return !warnings;
        }
        return value;
    }

    /**
     * informs whether to use experimental incremental compilation feature. See {@link #setIncremental(boolean)}
     */
    @Incubating
    public boolean isIncremental() {
        return incremental;
    }
}

