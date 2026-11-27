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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaForkOptions;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.internal.CollectionUtils;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Options for Scala platform compilation.
 * @since 2.3
 */
@SuppressWarnings("this-escape")
public abstract class BaseScalaCompileOptions implements Serializable {

    private static final long serialVersionUID = 0;

    private boolean failOnError = true;

    private boolean deprecation = true;

    private boolean unchecked = true;

    private String debugLevel;

    private boolean optimize;

    private String encoding;

    private boolean force;

    private final List<String> additionalParameters = new ArrayList<>();

    private final ListProperty<CommandLineArgumentProvider> compilerArgumentProviders = getObjectFactory().listProperty(CommandLineArgumentProvider.class);

    private boolean listFiles;

    private String loggingLevel;

    private List<String> loggingPhases;

    private ScalaForkOptions forkOptions = getObjectFactory().newInstance(ScalaForkOptions.class);

    private IncrementalCompileOptions incrementalOptions = getObjectFactory().newInstance(IncrementalCompileOptions.class);

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    /**
     * Fail the build on compilation errors.
     * @since 2.3
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * Sets the fail on error.
     *
     * @since 2.3
     */
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Generate deprecation information.
     * @since 2.3
     */
    @Console
    @ToBeReplacedByLazyProperty
    public boolean isDeprecation() {
        return deprecation;
    }

    /**
     * Sets the deprecation.
     *
     * @since 2.3
     */
    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    /**
     * Generate unchecked information.
     * @since 2.3
     */
    @Console
    @ToBeReplacedByLazyProperty
    public boolean isUnchecked() {
        return unchecked;
    }

    /**
     * Sets the unchecked.
     *
     * @since 2.3
     */
    public void setUnchecked(boolean unchecked) {
        this.unchecked = unchecked;
    }

    /**
     * Generate debugging information.
     * Legal values: none, source, line, vars, notailcalls
     * @since 2.3
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getDebugLevel() {
        return debugLevel;
    }

    /**
     * Sets the debug level.
     *
     * @since 2.3
     */
    public void setDebugLevel(@Nullable String debugLevel) {
        this.debugLevel = debugLevel;
    }

    /**
     * Run optimizations.
     * @since 2.3
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isOptimize() {
        return optimize;
    }

    /**
     * Sets the optimize.
     *
     * @since 2.3
     */
    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    /**
     * Encoding of source files.
     * @since 2.3
     */
    @ToBeReplacedByLazyProperty
    @Nullable
    @Optional
    @Input
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the encoding.
     *
     * @since 2.3
     */
    public void setEncoding(@Nullable String encoding) {
        this.encoding = encoding;
    }

    /**
     * Whether to force the compilation of all files.
     * Legal values:
     * - false (only compile modified files)
     * - true (always recompile all files)
     * @since 2.12
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isForce() {
        return force;
    }

    /**
     * Sets the force.
     *
     * @since 2.12
     */
    public void setForce(boolean force) {
        this.force = force;
    }

    /**
     * Additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     *
     * @return The list of additional parameters.
     * @since 2.3
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
     * @since 2.3
     */
    public void setAdditionalParameters(List<String> additionalParameters) {
        this.additionalParameters.clear();
        if (additionalParameters != null) {
            this.additionalParameters.addAll(additionalParameters);
        }
    }

    /**
     * Returns the Scala compiler argument providers. Defaults to an empty list.
     * <p>
     * Arguments from these providers are appended to {@link #getAdditionalParameters()} in registration order.
     * Replacing the additional parameters does not remove these providers.
     * Annotate provider properties to declare their inputs for up-to-date checking and build caching.
     * <p>
     * These arguments are passed to the Scala compiler, not the Java compiler used during joint compilation.
     *
     * @since 9.10.0
     */
    @Incubating
    @Nested
    public ListProperty<CommandLineArgumentProvider> getCompilerArgumentProviders() {
        return compilerArgumentProviders;
    }

    /**
     * Returns the additional parameters followed by arguments from {@link #getCompilerArgumentProviders()}.
     * <p>
     * Arguments retain their order and are not deduplicated. The returned provider calculates the combined
     * arguments when queried.
     *
     * @since 9.10.0
     */
    @Incubating
    @Internal
    public Provider<List<String>> getAllCompilerArgs() {
        return getCompilerArgumentProviders().map(providers -> {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            builder.addAll(CollectionUtils.stringize(getAdditionalParameters()));
            for (CommandLineArgumentProvider provider : providers) {
                builder.addAll(CollectionUtils.toStringList(provider.asArguments()));
            }
            return builder.build();
        });
    }

    /**
     * List files to be compiled.
     * @since 2.3
     */
    @Console
    @ToBeReplacedByLazyProperty
    public boolean isListFiles() {
        return listFiles;
    }

    /**
     * Sets the list files.
     *
     * @since 2.3
     */
    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    /**
     * Specifies the amount of logging.
     * Legal values:  none, verbose, debug
     * @since 2.3
     */
    @Console
    @ToBeReplacedByLazyProperty
    public String getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * Sets the logging level.
     *
     * @since 2.3
     */
    public void setLoggingLevel(String loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    /**
     * Phases of the compiler to log.
     * Legal values: namer, typer, pickler, uncurry, tailcalls, transmatch, explicitouter, erasure,
     * lambdalift, flatten, constructors, mixin, icode, jvm, terminal.
     * @since 2.3
     */
    @Console
    @ToBeReplacedByLazyProperty
    public List<String> getLoggingPhases() {
        return loggingPhases;
    }

    /**
     * Sets the logging phases.
     *
     * @since 2.3
     */
    public void setLoggingPhases(List<String> loggingPhases) {
        this.loggingPhases = loggingPhases;
    }

    /**
     * Options for running the Scala compiler in a separate process.
     * @since 2.3
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
     * @since 2.3
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
