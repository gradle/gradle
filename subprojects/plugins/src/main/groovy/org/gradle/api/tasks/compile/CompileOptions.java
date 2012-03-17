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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.util.DeprecationLogger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Main options for Java compilation.
 *
 * @author Hans Dockter
 */
public class CompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    /**
     * Specifies whether the compile task should fail when compilation fails. The default is {@code true}.
     */
    @Input
    private boolean failOnError = true;

    public boolean isFailOnError() {
        return failOnError;
    }

    // @Input not recognized if there is only an "is" method
    public boolean getFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Specifies whether the compile task should produce verbose output.
     */
    private boolean verbose;

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Specifies whether the compile task should list the files to be compiled.
     */
    private boolean listFiles;

    public boolean isListFiles() {
        return listFiles;
    }

    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    /**
     * Specifies whether to log details of usage of deprecated members or classes. The default is {@code false}.
     */
    private boolean deprecation;

    public boolean isDeprecation() {
        return deprecation;
    }

    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    /**
     * Specifies whether to log warning messages. The default is {@code true}.
     */
    private boolean warnings = true;

    public boolean isWarnings() {
        return warnings;
    }

    public void setWarnings(boolean warnings) {
        this.warnings = warnings;
    }

    /**
     * The source encoding name. Uses the platform default encoding if not specified. The default is {@code null}.
     */
    @Input @Optional
    private String encoding;

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Whether to produce optimized byte code. Note that this flag is ignored by Sun's javac starting with
     * JDK 1.3 (since compile-time optimization is unnecessary)
     */
    @Input
    private boolean optimize;

    public boolean isOptimize() {
        return optimize;
    }

    // @Input not recognized if there is only an "is" method
    public boolean getOptimize() {
        return optimize;
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    /**
     * Specifies whether debugging information should be included in the generated {@code .class} files. The default
     * is {@code true}. See {@link DebugOptions#debugLevel} for which debugging information will be generated.
     */
    @Input
    private boolean debug = true;

    public boolean isDebug() {
        return debug;
    }

    // @Input not recognized if there is only an "is" method
    public boolean getDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Options for generating debugging information.
     */
    @Nested
    private DebugOptions debugOptions = new DebugOptions();

    public DebugOptions getDebugOptions() {
        return debugOptions;
    }

    public void setDebugOptions(DebugOptions debugOptions) {
        this.debugOptions = debugOptions;
    }

    /**
     * Specifies whether to run the compiler in its own process. The default is {@code false}.
     */
    private boolean fork;

    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * The options for running the compiler in a child process.
     */
    @Nested
    private ForkOptions forkOptions = new ForkOptions();

    public ForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(ForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    /**
     * Specifies whether to use the Ant {@code <depend>} task.
     */
    private boolean useDepend;

    public boolean isUseDepend() {
        return useDepend;
    }

    public void setUseDepend(boolean useDepend) {
        this.useDepend = useDepend;
    }

    /**
     * The options for using the Ant {@code <depend>} task.
     */
    private DependOptions dependOptions = new DependOptions();

    public DependOptions getDependOptions() {
        return dependOptions;
    }

    public void setDependOptions(DependOptions dependOptions) {
        this.dependOptions = dependOptions;
    }

    /**
     * The compiler to use.
     */
    @Deprecated
    @Input @Optional
    private String compiler;

    public String getCompiler() {
        return compiler;
    }

    void setCompiler(String compiler) {
        DeprecationLogger.nagUserOfDiscontinuedProperty("CompileOptions.compiler", "To use an alternative compiler, "
                + "set 'CompileOptions.fork' to 'true', and 'CompileOptions.forkOptions.executable' to the path of the compiler executable.");
        this.compiler = compiler;
    }

    @Input
    private boolean includeJavaRuntime;

    public boolean isIncludeJavaRuntime() {
        return includeJavaRuntime;
    }

    // @Input not recognized if there is only an "is" method
    public boolean getIncludeJavaRuntime() {
        return includeJavaRuntime;
    }

    public void setIncludeJavaRuntime(boolean includeJavaRuntime) {
        this.includeJavaRuntime = includeJavaRuntime;
    }

    /**
     * The bootstrap classpath to use when compiling.
     */
    @Input @Optional
    private String bootClasspath;

    public String getBootClasspath() {
        return bootClasspath;
    }

    public void setBootClasspath(String bootClasspath) {
        this.bootClasspath = bootClasspath;
    }

    /**
     * The extension dirs to use when compiling.
     */
    @Input @Optional
    private String extensionDirs;

    public String getExtensionDirs() {
        return extensionDirs;
    }

    public void setExtensionDirs(String extensionDirs) {
        this.extensionDirs = extensionDirs;
    }

    /**
     * The arguments to pass to the compiler.
     */
    @Input
    private List<String> compilerArgs = Lists.newArrayList();

    public List<String> getCompilerArgs() {
        return compilerArgs;
    }

    public void setCompilerArgs(List<String> compilerArgs) {
        this.compilerArgs = compilerArgs;
    }

    /**
     * Whether to use the Ant javac task or Gradle's own Java compiler integration.
     * Defaults to <tt>false</tt>.
     */
    private boolean useAnt;

    public boolean isUseAnt() {
        return useAnt;
    }

    public void setUseAnt(boolean useAnt) {
        this.useAnt = useAnt;
    }

    /**
     * Convenience method to set fork options with named parameter syntax.
     */
    CompileOptions fork(Map<String, Object> forkArgs) {
        fork = true;
        forkOptions.define(forkArgs);
        return this;
    }

    /**
     * Convenience method to set debug options with named parameter syntax.
     */
    CompileOptions debug(Map<String, Object> debugArgs) {
        debug = true;
        debugOptions.define(debugArgs);
        return this;
    }

    /**
     * Set the dependency options from a map.  See  {@link DependOptions}  for
     * a list of valid properties.  Calling this method will enable use
     * of the depend task during a compile.
     */
    CompileOptions depend(Map<String, Object> dependArgs) {
        useDepend = true;
        dependOptions.define(dependArgs);
        return this;
    }

    protected List<String> excludedFieldsFromOptionMap() {
        return Arrays.asList("debugOptions", "forkOptions", "compilerArgs", "dependOptions", "useDepend", "useAnt");
    }

    protected Map<String, String> fieldName2AntMap() {
        return ImmutableMap.of("warnings", "nowarn", "bootClasspath", "bootclasspath", "extensionDirs", "extdirs", "failOnError", "failonerror", "listFiles", "listfiles");
    }

    protected Map<String, ? extends Callable<Object>> fieldValue2AntMap() {
        return ImmutableMap.of("warnings", new Callable<Object>() {
            public Object call() {
                return !warnings;
            }
        });
    }

    public Map<String, Object> optionMap() {
        Map<String, Object> map = super.optionMap();
        map.putAll(debugOptions.optionMap());
        map.putAll(forkOptions.optionMap());
        return map;
    }
}

