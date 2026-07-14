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
import org.gradle.api.file.FileCollection;
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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;

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
     * Sets the system properties to use for the process.
     *
     * @param systemProperties The system properties. Must not be null.
     */
    void setSystemProperties(Map<String, ? extends @Nullable Object> systemProperties);

    /**
     * Adds some system properties to use for the process.
     *
     * @param properties The system properties. Must not be null.
     * @return this
     */
    JavaForkOptions systemProperties(Map<String, ? extends @Nullable Object> properties);

    /**
     * Adds a system property to use for the process.
     *
     * @param name The name of the property
     * @param value The value for the property. May be null.
     * @return this
     */
    JavaForkOptions systemProperty(String name, @Nullable Object value);

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
     * Sets the default character encoding to use.
     *
     * Note: Many JVM implementations support the setting of this attribute via system property on startup (namely, the {@code file.encoding} property). For JVMs
     * where this is the case, setting the {@code file.encoding} property via {@link #setSystemProperties(java.util.Map)} or similar will have no effect as
     * this value will be overridden by the value specified by {@link #getDefaultCharacterEncoding()}.
     *
     * @param defaultCharacterEncoding The default character encoding. Use null to use {@link java.nio.charset.Charset#defaultCharset() this JVM's default charset}
     */
    void setDefaultCharacterEncoding(@Nullable String defaultCharacterEncoding);

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
     * Sets the minimum heap size for the process.
     * Supports the units megabytes (e.g. "512m") and gigabytes (e.g. "1g").
     *
     * @param minHeapSize The minimum heap size. Use null for the default minimum heap size.
     */
    void setMinHeapSize(@Nullable String minHeapSize);

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
     * Sets the maximum heap size for the process.
     * Supports the units megabytes (e.g. "512m") and gigabytes (e.g. "1g").
     *
     * @param maxHeapSize The heap size. Use null for the default maximum heap size.
     */
    void setMaxHeapSize(@Nullable String maxHeapSize);

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
     * Sets the extra arguments to use to launch the JVM for the process. System properties
     * and minimum/maximum heap size are updated.
     *
     * @param arguments The arguments. Must not be null.
     * @since 4.0
     */
    void setJvmArgs(List<String> arguments);

    /**
     * Sets the extra arguments to use to launch the JVM for the process. System properties
     * and minimum/maximum heap size are updated.
     *
     * @param arguments The arguments. Must not be null.
     */
    void setJvmArgs(Iterable<?> arguments);

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
     * Sets the bootstrap classpath to use for the process. Set to an empty classpath to use the default bootstrap
     * classpath for the specified JVM.
     *
     * @param bootstrapClasspath The classpath. Must not be null. Can be empty.
     */
    void setBootstrapClasspath(FileCollection bootstrapClasspath);

    /**
     * Adds the given values to the end of the bootstrap classpath for the process.
     *
     * @param classpath The classpath.
     * @return this
     */
    JavaForkOptions bootstrapClasspath(@Nullable Object... classpath);

    /**
     * A flag that marks if assertions are enabled for the process.
     */
    @Input
    @Optional
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = GETTER, name = "getEnableAssertions", originalType = boolean.class))
    Property<Boolean> getEnableAssertions();

    /**
     * Enable or disable assertions for the process.
     *
     * @param enableAssertions true to enable assertions, false to disable.
     */
    void setEnableAssertions(boolean enableAssertions);

    /**
     * Determines whether debugging is enabled for the test process. When enabled — {@code debug = true} — the process
     * is started in a suspended state, listening on port 5005. You should disable parallel test execution when
     * debugging and you will need to reattach the debugger occasionally if you use a non-zero value for
     * {@link org.gradle.api.tasks.testing.Test#getForkEvery()}.
     * <p>
     * Since Gradle 5.6, you can configure the port and other Java debug properties via
     * {@link #debugOptions(Action)}.
     */
    @Input
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = GETTER, name = "getDebug", originalType = boolean.class))
    Property<Boolean> getDebug();

    /**
     * Enable or disable debugging for the process. When enabled, the process is started suspended and listening on port
     * 5005.
     * <p>
     * The debug properties (e.g. the port number) can be configured in {@link #debugOptions(Action)}.
     *
     * @param debug true to enable debugging, false to disable.
     */
    void setDebug(boolean debug);

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
     * Sets the full set of arguments to use to launch the JVM for the process. Overwrites any previously set system
     * properties, minimum/maximum heap size, assertions, and bootstrap classpath.
     *
     * @param arguments The arguments. Must not be null.
     * @since 4.0
     */
    @Deprecated
    void setAllJvmArgs(List<String> arguments);

    /**
     * Sets the full set of arguments to use to launch the JVM for the process. Overwrites any previously set system
     * properties, minimum/maximum heap size, assertions, and bootstrap classpath.
     *
     * @param arguments The arguments. Must not be null.
     */
    @Deprecated
    void setAllJvmArgs(Iterable<?> arguments);

    /**
     * Copies these options to the given options.
     *
     * @param options The target options.
     * @return this
     */
    JavaForkOptions copyTo(JavaForkOptions options);
}
