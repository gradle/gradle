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
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
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
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import javax.inject.Inject;

/**
 * Options for Scala platform compilation.
 */
@SuppressWarnings("deprecation")
public abstract class BaseScalaCompileOptions extends org.gradle.api.tasks.compile.AbstractOptions {

    private static final long serialVersionUID = 0;

    private ScalaForkOptions forkOptions = getObjectFactory().newInstance(ScalaForkOptions.class);

    private IncrementalCompileOptions incrementalOptions = getObjectFactory().newInstance(IncrementalCompileOptions.class);

    private final Property<KeepAliveMode> keepAliveMode = getObjectFactory().property(KeepAliveMode.class);

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
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Fail the build on compilation errors.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getFailOnError();

    @Internal
    @Deprecated
    public Property<Boolean> getIsFailOnError() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsFailOnError()", "getFailOnError()");
        return getFailOnError();
    }

    /**
     * Generate deprecation information.
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getDeprecation();

    @Internal
    @Deprecated
    public Property<Boolean> getIsDeprecation() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsDeprecation()", "getDeprecation()");
        return getDeprecation();
    }

    /**
     * Generate unchecked information.
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getUnchecked();

    @Internal
    @Deprecated
    public Property<Boolean> getIsUnchecked() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsUnchecked()", "getUnchecked()");
        return getUnchecked();
    }

    /**
     * Generate debugging information.
     * Legal values: none, source, line, vars, notailcalls
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getDebugLevel();

    /**
     * Run optimizations.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getOptimize();

    @Internal
    @Deprecated
    public Property<Boolean> getIsOptimize() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsOptimize()", "getOptimize()");
        return getOptimize();
    }

    /**
     * Encoding of source files.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getEncoding();

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
    @Deprecated
    public Property<Boolean> getIsForce() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsForce()", "getForce()");
        return getForce();
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
     * List files to be compiled.
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getListFiles();

    @Internal
    @Deprecated
    public Property<Boolean> getIsListFiles() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsListFiles()", "getListFiles()");
        return getListFiles();
    }

    /**
     * Specifies the amount of logging.
     * Legal values:  none, verbose, debug
     */
    @Console
    @ReplacesEagerProperty
    public abstract Property<String> getLoggingLevel();

    /**
     * Phases of the compiler to log.
     * Legal values: namer, typer, pickler, uncurry, tailcalls, transmatch, explicitouter, erasure,
     *               lambdalift, flatten, constructors, mixin, icode, jvm, terminal.
     */
    @Console
    @ReplacesEagerProperty
    public abstract ListProperty<String> getLoggingPhases();

    /**
     * Options for running the Scala compiler in a separate process.
     */
    @Nested
    public ScalaForkOptions getForkOptions() {
        return forkOptions;
    }

    /**
     * Options for running the Scala compiler in a separate process.
     *
     * @deprecated Setting a new instance of this property is unnecessary. This method will be removed in Gradle 9.0. Use {@link #forkOptions(Action)} instead.
     */
    @Deprecated
    public void setForkOptions(ScalaForkOptions forkOptions) {
        DeprecationLogger.deprecateMethod(BaseScalaCompileOptions.class, "setForkOptions(ScalaForkOptions)")
            .replaceWith("forkOptions(Action)")
            .withContext("Setting a new instance of forkOptions is unnecessary.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_nested_properties_setters")
            .nagUser();
        this.forkOptions = forkOptions;
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
     * Options for incremental compilation of Scala code.
     *
     * @deprecated Setting a new instance of this property is unnecessary. This method will be removed in Gradle 9.0. Use {@link #incrementalOptions(Action)} instead.
     */
    @Deprecated
    public void setIncrementalOptions(IncrementalCompileOptions incrementalOptions) {
        DeprecationLogger.deprecateMethod(BaseScalaCompileOptions.class, "setIncrementalOptions(IncrementalCompileOptions)")
            .replaceWith("scalaDocOptions(Action)")
            .withContext("Setting a new instance of scalaDocOptions is unnecessary.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_nested_properties_setters")
            .nagUser();
        this.incrementalOptions = incrementalOptions;
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
    public Property<KeepAliveMode> getKeepAliveMode() {
        return this.keepAliveMode;
    }
}
