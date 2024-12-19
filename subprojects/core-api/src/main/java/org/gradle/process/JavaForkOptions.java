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

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import java.util.List;
import java.util.Map;

/**
 * <p>Specifies the options to use to fork a Java process.</p>
 */
@HasInternalProtocol
public interface JavaForkOptions extends ProcessForkOptions {

    /**
     * System properties which will be used for the process.
     *
     * @return The system properties. Returns an empty map when there are no system properties.
     */
    @Input
    @ReplacesEagerProperty
    MapProperty<String, Object> getSystemProperties();

    /**
     * Adds some system properties to use for the process.
     *
     * @param properties The system properties. Must not be null.
     * @return this
     */
    JavaForkOptions systemProperties(Map<String, ?> properties);

    /**
     * Adds a system property to use for the process.
     *
     * @param name The name of the property
     * @param value The value for the property. May be null.
     * @return this
     */
    JavaForkOptions systemProperty(String name, Object value);

    /**
     * Returns the default character encoding to use.
     *
     * @return The default character encoding. Returns null if the {@link java.nio.charset.Charset#defaultCharset() default character encoding of this JVM} should be used.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    Property<String> getDefaultCharacterEncoding();

    /**
     * Returns the minimum heap size for the process, if any.
     * Supports the units megabytes (e.g. "512m") and gigabytes (e.g. "1g").
     *
     * @return The minimum heap size. Returns null if the default minimum heap size should be used.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    Property<String> getMinHeapSize();

    /**
     * Returns the maximum heap size for the process, if any.
     * Supports the units megabytes (e.g. "512m") and gigabytes (e.g. "1g").
     *
     * @return The maximum heap size. Returns null if the default maximum heap size should be used.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    Property<String> getMaxHeapSize();

    /**
     * The extra arguments to use to launch the JVM for the process.
     *
     * @return The list of arguments. Returns an empty list if there are no arguments.
     */
    @Optional
    @Input
    @ReplacesEagerProperty(adapter = JavaForkOptionsAdapters.JvmArgsAdapter.class)
    ListProperty<String> getJvmArgs();

    /**
     * Adds some arguments to use to launch the JVM for the process.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    JavaForkOptions jvmArgs(Iterable<?> arguments);

    /**
     * Adds some arguments to use to launch the JVM for the process.
     *
     * @param arguments The arguments.
     * @return this
     */
    JavaForkOptions jvmArgs(Object... arguments);

    /**
     * Command line argument providers for the java process to fork.
     *
     * @since 4.6
     */
    @Nested
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = AccessorType.GETTER, name = "getJvmArgumentProviders"))
    ListProperty<CommandLineArgumentProvider> getJvmArgumentProviders();

    /**
     * Returns the bootstrap classpath to use for the process. The default bootstrap classpath for the JVM is used when
     * this classpath is empty.
     *
     * @return The bootstrap classpath. Never returns null.
     */
    @Classpath
    @ReplacesEagerProperty
    ConfigurableFileCollection getBootstrapClasspath();

    /**
     * Adds the given values to the end of the bootstrap classpath for the process.
     *
     * @param classpath The classpath.
     * @return this
     */
    JavaForkOptions bootstrapClasspath(Object... classpath);

    /**
     * A flag that marks if assertions are enabled for the process.
     */
    @Input
    @Optional
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = AccessorType.GETTER, name = "getEnableAssertions", originalType = boolean.class),
        @ReplacedAccessor(value = AccessorType.SETTER, name = "setEnableAssertions", originalType = boolean.class)
    })
    Property<Boolean> getEnableAssertions();

    /**
     * Determines whether debugging is enabled for the test process. When enabled — {@code debug = true} — the process
     * is started in a suspended state, listening on port 5005. You should disable parallel test execution when
     * debugging and you will need to reattach the debugger occasionally if you use a non-zero value for
     * {@link org.gradle.api.tasks.testing.Test#getForkEvery()}.
     * <p>
     * Since Gradle 5.6, you can configure the port and other Java debug properties via
     * {@link #debugOptions(Action)}.
     */
    @Input
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = AccessorType.GETTER, name = "getDebug", originalType = boolean.class),
        @ReplacedAccessor(value = AccessorType.SETTER, name = "setDebug", originalType = boolean.class)
    })
    Property<Boolean> getDebug();

    /**
     * Returns the Java Debug Wire Protocol properties for the process. If enabled then the {@code -agentlib:jdwp=...}
     * will be appended to the JVM arguments with the configuration from the parameter.
     *
     * @since 5.6
     */
    @Nested
    JavaDebugOptions getDebugOptions();

    /**
     * Configures Java Debug Wire Protocol properties for the process. If {@link #getDebug()} (boolean)} is enabled then
     * the {@code -agentlib:jdwp=...}  will be appended to the JVM arguments with the configuration from the parameter.
     *
     * @param action the Java debug configuration
     * @since 5.6
     */
    void debugOptions(Action<JavaDebugOptions> action);

    /**
     * Returns the full set of arguments to use to launch the JVM for the process. This includes arguments to define
     * system properties, the minimum/maximum heap size, and the bootstrap classpath.
     *
     * @return The immutable list of arguments. Returns an empty list if there are no arguments.
     */
    @Internal
    @ReplacesEagerProperty(adapter = JavaForkOptionsAdapters.AllJvmArgsAdapter.class)
    Provider<List<String>> getAllJvmArgs();

    /**
     * Copies these options to the given options.
     *
     * @param options The target options.
     * @return this
     */
    JavaForkOptions copyTo(JavaForkOptions options);
}
