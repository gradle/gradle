/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link JavaExec}.
 *
 * <p>The JVM arguments are stored in {@link JavaExec#getJvmArguments()} and the debug flag in
 * {@link JavaExec#getDebugOptions()}, which are lazy already.</p>
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface JavaExecInputs {

    /**
     * The Java executable that was set explicitly. The process uses the executable of the Java launcher.
     *
     * @see JavaExec#getExecutable()
     * @since 10.0.0
     */
    Property<String> getExecutable();

    /**
     * The arguments of the main class, without the arguments of the argument providers.
     *
     * @see JavaExec#getArgs()
     * @since 10.0.0
     */
    ListProperty<String> getArgs();

    /**
     * The environment variables of the process. The convention is the environment that the process inherits from Gradle.
     *
     * @see JavaExec#getEnvironment()
     * @since 10.0.0
     */
    MapProperty<String, Object> getEnvironment();

    /**
     * Whether a non-zero exit value of the process is ignored.
     *
     * @see JavaExec#isIgnoreExitValue()
     * @since 10.0.0
     */
    Property<Boolean> getIgnoreExitValue();

    /**
     * The classpath of the main class.
     *
     * @see JavaExec#getClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getClasspath();

    /**
     * The system properties of the process.
     *
     * @see JavaExec#getSystemProperties()
     * @since 10.0.0
     */
    MapProperty<String, Object> getSystemProperties();

    /**
     * The minimum heap size of the process.
     *
     * @see JavaExec#getMinHeapSize()
     * @since 10.0.0
     */
    Property<String> getMinHeapSize();

    /**
     * The maximum heap size of the process.
     *
     * @see JavaExec#getMaxHeapSize()
     * @since 10.0.0
     */
    Property<String> getMaxHeapSize();

    /**
     * Whether assertions are enabled in the process.
     *
     * @see JavaExec#getEnableAssertions()
     * @since 10.0.0
     */
    Property<Boolean> getEnableAssertions();

    /**
     * The default character encoding of the process.
     *
     * @see JavaExec#getDefaultCharacterEncoding()
     * @since 10.0.0
     */
    Property<String> getDefaultCharacterEncoding();

    /**
     * The bootstrap classpath of the process.
     *
     * @see JavaExec#getBootstrapClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getBootstrapClasspath();
}
