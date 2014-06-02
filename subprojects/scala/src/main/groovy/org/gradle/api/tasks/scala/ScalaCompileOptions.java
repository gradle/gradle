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
import com.google.common.collect.ImmutableMap;

import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.compile.AbstractOptions;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import java.util.List;

/**
 * Options for Scala compilation.
 */
public class ScalaCompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private static final ImmutableMap<String, String> FIELD_NAMES_TO_ANT_PROPERTIES = new ImmutableMap.Builder<String, String>()
            .put("loggingLevel", "logging")
            .put("loggingPhases", "logphase")
            .put("targetCompatibility", "target")
            .put("optimize", "optimise")
            .put("daemonServer", "server")
            .put("listFiles", "scalacdebugging")
            .put("debugLevel", "debuginfo")
            .put("additionalParameters", "addparams")
            .build();

    private boolean useCompileDaemon;

    private String daemonServer;

    private boolean failOnError = true;

    private boolean deprecation = true;

    private boolean unchecked = true;

    private String debugLevel;

    private boolean optimize;

    private String encoding;

    private String force = "never";

    private List<String> additionalParameters;

    private boolean listFiles;

    private String loggingLevel;

    private List<String> loggingPhases;

    private boolean fork;

    private ScalaForkOptions forkOptions = new ScalaForkOptions();

    private boolean useAnt = true;

    private IncrementalCompileOptions incrementalOptions = new IncrementalCompileOptions();

    /**
     * Whether to use the fsc compile daemon.
     */
    public boolean isUseCompileDaemon() {
        return useCompileDaemon;
    }

    public void setUseCompileDaemon(boolean useCompileDaemon) {
        this.useCompileDaemon = useCompileDaemon;
    }

    // NOTE: Does not work for scalac 2.7.1 due to a bug in the Ant task
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
     * Whether to run the Scala compiler in a separate process. Defaults to {@code false}
     * for the Ant based compiler ({@code useAnt = true}), and to {@code true} for the Zinc
     * based compiler ({@code useAnt = false}).
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
    public ScalaForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(ScalaForkOptions forkOptions) {
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
        if (!useAnt) {
            setFork(true);
        }
    }

    @Nested
    public IncrementalCompileOptions getIncrementalOptions() {
        return incrementalOptions;
    }

    public void setIncrementalOptions(IncrementalCompileOptions incrementalOptions) {
        this.incrementalOptions = incrementalOptions;
    }

    protected boolean excludeFromAntProperties(String fieldName) {
        return fieldName.equals("useCompileDaemon")
                || fieldName.equals("forkOptions")
                || fieldName.equals("useAnt")
                || fieldName.equals("incrementalOptions")
                || fieldName.equals("targetCompatibility") // handled directly by AntScalaCompiler
                || fieldName.equals("optimize") && !optimize;
    }

    protected String getAntPropertyName(String fieldName) {
        if (FIELD_NAMES_TO_ANT_PROPERTIES.containsKey(fieldName)) {
            return FIELD_NAMES_TO_ANT_PROPERTIES.get(fieldName);
        }
        return fieldName;
    }

    protected Object getAntPropertyValue(String fieldName, Object value) {
        if (fieldName.equals("deprecation")) {
            return toOnOffString(isDeprecation());
        }
        if (fieldName.equals("unchecked")) {
            return toOnOffString(isUnchecked());
        }
        if (fieldName.equals("optimize")) {
            return toOnOffString(isOptimize());
        }
        if (fieldName.equals("loggingPhases")) {
            return getLoggingPhases().isEmpty() ? " " : Joiner.on(',').join(getLoggingPhases());
        }
        if (fieldName.equals("additionalParameters")) {
            return getAdditionalParameters().isEmpty() ? " " : Joiner.on(' ').join(getAdditionalParameters());
        }
        return value;
    }

    private String toOnOffString(boolean flag) {
        return flag ? "on" : "off";
    }
}
