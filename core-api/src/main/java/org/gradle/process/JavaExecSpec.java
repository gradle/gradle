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

import org.gradle.api.file.FileCollection;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Specifies the options for executing a Java application.
 */
public interface JavaExecSpec extends JavaForkOptions, BaseExecSpec {

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
     * <p>
     * Use this property instead of {@link #getMain()} and {@link #setMain(String)}.
     *
     * @since 6.4
     */
    @Optional
    @Input
    Property<String> getMainClass();

    /**
     * Returns the fully qualified name of the Main class to be executed.
     * <p>
     * This does not need to be set if using an <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html">Executable Jar</a> with a {@code Main-Class} attribute.
     * </p>
     *
     * @deprecated Use {@link #getMainClass()} instead. This method will be removed in Gradle 8.0.
     */
    @Deprecated
    @Nullable @Optional
    @ReplacedBy("mainClass")
    String getMain();

    /**
     * Sets the fully qualified name of the main class to be executed.
     *
     * @param main the fully qualified name of the main class to be executed.
     *
     * @return this
     *
     * @deprecated Use {@link #getMainClass()}.set(main) instead. This method will be removed in Gradle 8.0.
     */
    @Deprecated
    @ReplacedBy("mainClass")
    JavaExecSpec setMain(@Nullable String main);

    /**
     * Returns the arguments passed to the main class to be executed.
     */
    @Nullable @Optional @Input
    List<String> getArgs();

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
     * Sets the args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     * @since 4.0
     */
    JavaExecSpec setArgs(@Nullable List<String> args);

    /**
     * Sets the args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     */
    JavaExecSpec setArgs(@Nullable Iterable<?> args);

    /**
     * Argument providers for the application.
     *
     * @since 4.6
     */
    @Nested
    List<CommandLineArgumentProvider> getArgumentProviders();

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
    FileCollection getClasspath();

    /**
     * Sets the classpath for executing the main class.
     *
     * @param classpath the classpath
     *
     * @return this
     */
    JavaExecSpec setClasspath(FileCollection classpath);

    /**
     * Returns the module path handling for executing the main class.
     *
     * @since 6.4
     */
    @Nested
    ModularitySpec getModularity();
}
