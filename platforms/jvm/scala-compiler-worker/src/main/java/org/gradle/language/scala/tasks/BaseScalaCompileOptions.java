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

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaForkOptions;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.List;

/**
 * Options for Scala platform compilation.
 */
public abstract class BaseScalaCompileOptions implements Serializable {

    private static final long serialVersionUID = 0;

    private final ScalaForkOptions forkOptions = getObjectFactory().newInstance(ScalaForkOptions.class);

    private final IncrementalCompileOptions incrementalOptions = getObjectFactory().newInstance(IncrementalCompileOptions.class);

    @Inject
    public BaseScalaCompileOptions() {
        getFailOnError().convention(true);
        getDeprecation().convention(true);
        getUnchecked().convention(true);
        getOptimize().convention(false);
        getForce().convention(false);
        getListFiles().convention(false);
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    /**
     * Fail the build on compilation errors.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getFailOnError();

    @Internal
    public Property<Boolean> getIsFailOnError() {
        return getFailOnError();
    }

    public void setFailOnError(boolean failOnError) {
        getFailOnError().set(failOnError);
    }

    /**
     * Generate deprecation information.
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getDeprecation();

    @Internal
    public Property<Boolean> getIsDeprecation() {
        return getDeprecation();
    }

    public void setDeprecation(boolean deprecation) {
        getDeprecation().set(deprecation);
    }

    /**
     * Generate unchecked information.
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getUnchecked();

    @Internal
    public Property<Boolean> getIsUnchecked() {
        return getUnchecked();
    }

    public void setUnchecked(boolean unchecked) {
        getUnchecked().set(unchecked);
    }

    /**
     * Generate debugging information.
     * Legal values: none, source, line, vars, notailcalls
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getDebugLevel();

    public void setDebugLevel(@Nullable String debugLevel) {
        getDebugLevel().set(debugLevel);
    }

    /**
     * Run optimizations.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getOptimize();

    @Internal
    public Property<Boolean> getIsOptimize() {
        return getOptimize();
    }

    public void setOptimize(boolean optimize) {
        getOptimize().set(optimize);
    }

    /**
     * Encoding of source files.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getEncoding();

    public void setEncoding(@Nullable String encoding) {
        getEncoding().set(encoding);
    }

    /**
     * Whether to force the compilation of all files.
     * Legal values:
     * - false (only compile modified files)
     * - true (always recompile all files)
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getForce();

    @Internal
    public Property<Boolean> getIsForce() {
        return getForce();
    }

    public void setForce(boolean force) {
        getForce().set(force);
    }

    /**
     * Additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     *
     * @return The list of additional parameters.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract ListProperty<String> getAdditionalParameters();

    /**
     * Sets the additional parameters.
     * <p>
     * Setting this property will clear any previously set additional parameters.
     */
    public void setAdditionalParameters(List<String> additionalParameters) {
        getAdditionalParameters().set(additionalParameters);
    }

    /**
     * List files to be compiled.
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getListFiles();

    @Internal
    public Property<Boolean> getIsListFiles() {
        return getListFiles();
    }

    public void setListFiles(boolean listFiles) {
        getListFiles().set(listFiles);
    }

    /**
     * Specifies the amount of logging.
     * Legal values:  none, verbose, debug
     */
    @Console
    @ReplacesEagerProperty
    public abstract Property<String> getLoggingLevel();

    public void setLoggingLevel(String loggingLevel) {
        getLoggingLevel().set(loggingLevel);
    }

    /**
     * Phases of the compiler to log.
     * Legal values: namer, typer, pickler, uncurry, tailcalls, transmatch, explicitouter, erasure,
     * lambdalift, flatten, constructors, mixin, icode, jvm, terminal.
     */
    @Console
    @ReplacesEagerProperty
    public abstract ListProperty<String> getLoggingPhases();

    public void setLoggingPhases(List<String> loggingPhases) {
        getLoggingPhases().set(loggingPhases);
    }

    /**
     * Options for running the Scala compiler in a separate process.
     */
    @Nested
    public ScalaForkOptions getForkOptions() {
        return forkOptions;
    }

    /**
     * Configure options for running the Scala compiler in a separate process.
     *
     * @since 8.11
     */
    public void forkOptions(Action<? super ScalaForkOptions> action) {
        action.execute(forkOptions);
    }

    /**
     * Options for incremental compilation of Scala code.
     */
    @Nested
    public IncrementalCompileOptions getIncrementalOptions() {
        return incrementalOptions;
    }

    /**
     * Configure options for incremental compilation of Scala code.
     *
     * @since 8.11
     */
    public void incrementalOptions(Action<? super IncrementalCompileOptions> action) {
        action.execute(incrementalOptions);
    }

    /**
     * Keeps Scala compiler daemon alive across builds for faster build times
     *
     * @since 7.6
     */
    @Incubating
    @Input
    public abstract Property<KeepAliveMode> getKeepAliveMode();
}
