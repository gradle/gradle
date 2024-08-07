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

package org.gradle.language.scala.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaForkOptions;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Options for Scala platform compilation.
 */
@SuppressWarnings("deprecation")
public abstract class BaseScalaCompileOptions extends org.gradle.api.tasks.compile.AbstractOptions {

    private static final long serialVersionUID = 0;

    private boolean failOnError = true;

    private boolean deprecation = true;

    private boolean unchecked = true;

    private String debugLevel;

    private boolean optimize;

    private String encoding;

    private boolean force;

    private final List<String> additionalParameters = new ArrayList<>();

    private boolean listFiles;

    private String loggingLevel;

    private List<String> loggingPhases;

    private ScalaForkOptions forkOptions = getObjectFactory().newInstance(ScalaForkOptions.class);

    private IncrementalCompileOptions incrementalOptions;

    private final Property<KeepAliveMode> keepAliveMode = getObjectFactory().property(KeepAliveMode.class);

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Fail the build on compilation errors.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Generate deprecation information.
     */
    @Console
    @ToBeReplacedByLazyProperty
    public boolean isDeprecation() {
        return deprecation;
    }

    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    /**
     * Generate unchecked information.
     */
    @Console
    @ToBeReplacedByLazyProperty
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
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(@Nullable String debugLevel) {
        this.debugLevel = debugLevel;
    }

    /**
     * Run optimizations.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isOptimize() {
        return optimize;
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    /**
     * Encoding of source files.
     */
    @ToBeReplacedByLazyProperty
    @Nullable @Optional @Input
    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(@Nullable String encoding) {
        this.encoding = encoding;
    }

    /**
     * Whether to force the compilation of all files.
     * Legal values:
     * - false (only compile modified files)
     * - true (always recompile all files)
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    /**
     * Additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     *
     * @return The list of additional parameters.
     */
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    /**
     * Sets the additional parameters.
     * <p>
     * Setting this property will clear any previously set additional parameters.
     */
    public void setAdditionalParameters(List<String> additionalParameters) {
        this.additionalParameters.clear();
        if (additionalParameters != null) {
            this.additionalParameters.addAll(additionalParameters);
        }
    }

    /**
     * List files to be compiled.
     */
    @Console
    @ToBeReplacedByLazyProperty
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
    @Console
    @ToBeReplacedByLazyProperty
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
    @Console
    @ToBeReplacedByLazyProperty
    public List<String> getLoggingPhases() {
        return loggingPhases;
    }

    public void setLoggingPhases(List<String> loggingPhases) {
        this.loggingPhases = loggingPhases;
    }

    /**
     * Options for running the Scala compiler in a separate process.
     */
    @Nested
    public ScalaForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(ScalaForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    /**
     * Options for incremental compilation of Scala code.
     */
    @Nested
    public IncrementalCompileOptions getIncrementalOptions() {
        return incrementalOptions;
    }

    public void setIncrementalOptions(IncrementalCompileOptions incrementalOptions) {
        this.incrementalOptions = incrementalOptions;
    }

    /**
     * Keeps Scala compiler daemon alive across builds for faster build times
     *
     * @since 7.6
     */
    @Incubating
    @Input
    public Property<KeepAliveMode> getKeepAliveMode() {
        return this.keepAliveMode;
    }
}
