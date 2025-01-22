/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.process;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation.RemovedIn;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import javax.annotation.Nullable;

import static org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility.ACCESSORS_KEPT;

/**
 * Specifies the options for executing a Java application.
 */
public interface JavaExecSpec extends JavaForkOptions, BaseExecSpec {

    /**
     * Extra JVM arguments to be to use to launch the JVM for the process.
     *
     * Must be used to set a convention for JVM arguments.
     *
     * @since 8.1
     */
    @Incubating
    @Optional
    @Internal
    ListProperty<String> getJvmArguments();

    /**
     * The name of the main module to be executed if the application should run as a Java module.
     *
     * @since 6.4
     */
    @Optional
    @Input
    Property<String> getMainModule();

    /**
     * The fully qualified name of the Main class to be executed.
     * <p>
     * This does not need to be set if using an <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html">Executable Jar</a> with a {@code Main-Class} attribute.
     *
     * @since 6.4
     */
    @Optional
    @Input
    @ReplacesEagerProperty(
        replacedAccessors = @ReplacedAccessor(value = AccessorType.SETTER, name = "setMain", fluentSetter = true),
        binaryCompatibility = ACCESSORS_KEPT,
        deprecation = @ReplacedDeprecation(removedIn = RemovedIn.GRADLE9, withDslReference = true)
    )
    Property<String> getMainClass();

    /**
     * Sets the fully qualified name of the main class to be executed.
     *
     * @param main the fully qualified name of the main class to be executed.
     *
     * @return this
     *
     * @deprecated Use {@link #getMainClass()}.set(main) instead. This method will be removed in Gradle 9.0.
     */
    @Deprecated
    @ReplacedBy("mainClass")
    default JavaExecSpec setMain(@Nullable String main) {
        DeprecationLogger.deprecateProperty(JavaExecSpec.class, "main")
                .replaceWith("mainClass")
                .willBeRemovedInGradle9()
                .withDslReference()
                .nagUser();

        getMainClass().set(main);
        return this;
    }

    /**
     * Returns the arguments passed to the main class to be executed.
     */
    @Optional
    @Input
    @ReplacesEagerProperty(adapter = JavaExecSpecAdapters.ArgsAdapter.class)
    ListProperty<String> getArgs();

    /**
     * Adds args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     */
    JavaExecSpec args(Object... args);

    /**
     * Adds args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     */
    JavaExecSpec args(Iterable<?> args);

    /**
     * Argument providers for the application.
     *
     * @since 4.6
     */
    @Nested
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = AccessorType.GETTER, name = "getArgumentProviders")
    })
    ListProperty<CommandLineArgumentProvider> getArgumentProviders();

    /**
     * Adds elements to the classpath for executing the main class.
     *
     * @param paths classpath elements
     *
     * @return this
     */
    JavaExecSpec classpath(Object... paths);

    /**
     * Returns the classpath for executing the main class.
     */
    @Classpath
    @ReplacesEagerProperty(fluentSetter = true)
    ConfigurableFileCollection getClasspath();

    /**
     * Returns the module path handling for executing the main class.
     *
     * @since 6.4
     */
    @Nested
    ModularitySpec getModularity();
}
