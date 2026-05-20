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
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility;

import javax.inject.Inject;
import java.io.Serializable;

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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isFailOnError", originalType = boolean.class, binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setFailOnError", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getFailOnError();

    @Internal
    public Property<Boolean> getIsFailOnError() {
        return getFailOnError();
    }

    /**
     * Fail the build on compilation errors.
     *
     * @deprecated Use {@link #getFailOnError()} instead.
     */
    @Internal
    @Deprecated
    public boolean isFailOnError() {
        return getFailOnError().get();
    }

    /**
     * Generate deprecation information.
     */
    @Console
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isDeprecation", originalType = boolean.class, binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setDeprecation", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getDeprecation();

    @Internal
    public Property<Boolean> getIsDeprecation() {
        return getDeprecation();
    }

    /**
     * Generate deprecation information.
     *
     * @deprecated Use {@link #getDeprecation()} instead.
     */
    @Internal
    @Deprecated
    public boolean isDeprecation() {
        return getDeprecation().get();
    }

    /**
     * Generate unchecked information.
     */
    @Console
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isUnchecked", originalType = boolean.class, binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setUnchecked", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getUnchecked();

    @Internal
    public Property<Boolean> getIsUnchecked() {
        return getUnchecked();
    }

    /**
     * Generate unchecked information.
     *
     * @deprecated Use {@link #getUnchecked()} instead.
     */
    @Internal
    @Deprecated
    public boolean isUnchecked() {
        return getUnchecked().get();
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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isOptimize", originalType = boolean.class, binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setOptimize", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getOptimize();

    @Internal
    public Property<Boolean> getIsOptimize() {
        return getOptimize();
    }

    /**
     * Run optimizations.
     *
     * @deprecated Use {@link #getOptimize()} instead.
     */
    @Internal
    @Deprecated
    public boolean isOptimize() {
        return getOptimize().get();
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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isForce", originalType = boolean.class, binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setForce", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getForce();

    @Internal
    public Property<Boolean> getIsForce() {
        return getForce();
    }

    /**
     * Whether to force the compilation of all files.
     * Legal values:
     * - false (only compile modified files)
     * - true (always recompile all files)
     *
     * @deprecated Use {@link #getForce()} instead.
     */
    @Internal
    @Deprecated
    public boolean isForce() {
        return getForce().get();
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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isListFiles", originalType = boolean.class, binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setListFiles", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getListFiles();

    @Internal
    public Property<Boolean> getIsListFiles() {
        return getListFiles();
    }

    /**
     * List files to be compiled.
     *
     * @deprecated Use {@link #getListFiles()} instead.
     */
    @Internal
    @Deprecated
    public boolean isListFiles() {
        return getListFiles().get();
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
     * lambdalift, flatten, constructors, mixin, icode, jvm, terminal.
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
