/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.tasks.compile.AbstractOptions;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.compile.BaseForkOptions;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Options for Scala compilation.
 */
public class ScalaCompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private boolean useCompileDaemon;

    private String daemonServer;

    private boolean failOnError = true;

    private boolean deprecation = true;

    private boolean unchecked = true;

    private String debugLevel;

    private boolean optimize;

    private String encoding;

    private String force = "never";

    private String targetCompatibility = "1.5";

    private List<String> additionalParameters;

    private boolean listFiles;

    private String loggingLevel;

    private List<String> loggingPhases;

    private boolean fork;

    private BaseForkOptions forkOptions = new BaseForkOptions();

    private boolean useAnt = true;

    private File compilerCacheFile;

    /**
     * Whether to use the fsc compile daemon.
     */
    public boolean isUseCompileDaemon() {
        return useCompileDaemon;
    }

    public void setUseCompileDaemon(boolean useCompileDaemon) {
        this.useCompileDaemon = useCompileDaemon;
    }

    // NOTE: Does not work for scalac 2.7.1 due to a bug in the ant task
    /**
     * Server (host:port) on which the compile daemon is running.
     * The host must share disk access with the client process.
     * If not specified, launches the daemon on the localhost.
     * This parameter can only be specified if useCompileDaemon is true.
     */
    public String getDaemonServer() {
        return daemonServer;
    }

    public void setDaemonServer(String daemonServer) {
        this.daemonServer = daemonServer;
    }

    /**
     * Fail the build on compilation errors.
     */
    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Generate deprecation information.
     */
    public boolean isDeprecation() {
        return deprecation;
    }

    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    /**
     * Generate unchecked information.
     */
    public boolean isUnchecked() {
        return unchecked;
    }

    public void setUnchecked(boolean unchecked) {
        this.unchecked = unchecked;
    }

    /**
     * Generate debugging information.
     * Legal values: none, source, line, vars, notailcalls
     */
    @Input @Optional
    public String getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(String debugLevel) {
        this.debugLevel = debugLevel;
    }

    /**
     * Run optimizations.
     */
    @Input
    public boolean isOptimize() {
        return optimize;
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    /**
     * Encoding of source files.
     */
    @Input @Optional
    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Whether to force the compilation of all files.
     * Legal values:
     * - never (only compile modified files)
     * - changed (compile all files when at least one file is modified)
     * - always (always recompile all files)
     */
    public String getForce() {
        return force;
    }

    public void setForce(String force) {
        this.force = force;
    }

    /**
     * Specifies which backend to use.
     * Legal values: 1.4, 1.5
     */
    @Input
    public String getTargetCompatibility() {
        return targetCompatibility;
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    /**
     * Additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     */
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(List<String> additionalParameters) {
        this.additionalParameters = additionalParameters;
    }

    /**
     * List files to be compiled.
     */
    public boolean isListFiles() {
        return listFiles;
    }

    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    /**
     * Specifies the amount of logging.
     * Legal values:  none, verbose, debug
     */
    public String getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(String loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    /**
     * Phases of the compiler to log.
     * Legal values: namer, typer, pickler, uncurry, tailcalls, transmatch, explicitouter, erasure,
     *               lambdalift, flatten, constructors, mixin, icode, jvm, terminal.
     */
    public List<String> getLoggingPhases() {
        return loggingPhases;
    }

    public void setLoggingPhases(List<String> loggingPhases) {
        this.loggingPhases = loggingPhases;
    }

    /**
     * Whether to run the Scala compiler in a separate process. Defaults to {@code false}.
     */
    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Options for running the Scala compiler in a separate process. These options only take effect
     * if {@code fork} is set to {@code true}.
     */
    public BaseForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(BaseForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    /**
     * Tells whether to use Ant for compilation. If {@code true}, the standard Ant scalac (or fsc) task will be used for
     * Scala and Java joint compilation. If {@code false}, the Zinc incremental compiler will be used
     * instead. The latter can be significantly faster, especially if there are few source code changes
     * between compiler runs. Defaults to {@code true}.
     */
    public boolean isUseAnt() {
        return useAnt;
    }

    public void setUseAnt(boolean useAnt) {
        this.useAnt = useAnt;
    }

    /**
     * File location to store results of dependency analysis. Only used if {@code useAnt} is {@code true}.
     */
    public File getCompilerCacheFile() {
        return compilerCacheFile;
    }

    public void setCompilerCacheFile(File compilerCacheFile) {
        this.compilerCacheFile = compilerCacheFile;
    }

    protected Map<String, String> fieldName2AntMap() {
        return new ImmutableMap.Builder<String, String>()
                .put("failOnError", "failonerror")
                .put("loggingLevel", "logging")
                .put("loggingPhases", "logPhase")
                .put("targetCompatibility", "target")
                .put("optimize", "optimise")
                .put("daemonServer", "server")
                .put("listFiles", "scalacDebugging")
                .put("debugLevel", "debugInfo")
                .put("additionalParameters", "addParams")
                .build();
    }

    protected Map<String, ? extends Callable<Object>> fieldValue2AntMap() {
        return new ImmutableMap.Builder<String, Callable<Object>>()
                .put("deprecation", new Callable<Object>() {
                    public Object call() throws Exception {
                        return toOnOffString(isDeprecation());
                    }
                })
                .put("unchecked", new Callable<Object>() {
                    public Object call() throws Exception {
                        return toOnOffString(isUnchecked());
                    }
                })
                .put("optimize", new Callable<Object>() {
                    public Object call() throws Exception {
                        return toOnOffString(isOptimize());
                    }
                })
                .put("targetCompatibility", new Callable<Object>() {
                    public Object call() throws Exception {
                        return String.format("jvm-%s", getTargetCompatibility());
                    }
                })
                .put("loggingPhases", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getLoggingPhases().isEmpty() ? " " : Joiner.on(',').join(getLoggingPhases());
                    }
                })
                .put("additionalParameters", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getAdditionalParameters().isEmpty() ? " " : Joiner.on(' ').join(getAdditionalParameters());
                    }
                })
                .build();
    }

    protected List<String> excludedFieldsFromOptionMap() {
        ImmutableList.Builder<String> builder = new ImmutableList.Builder<String>()
                .add("useCompileDaemon", "forkOptions", "useAnt", "compilerCacheFile");
        if (!optimize) {
            builder.add("optimize");
        }
        return builder.build();
    }

    private String toOnOffString(boolean flag) {
        return flag ? "on" : "off";
    }
}
