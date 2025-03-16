/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.file.CopySpec;
import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

/**
 * Configuration for a Java application, defining how to assemble the application.
 * <p>
 * An instance of this type is added as a project extension by the Java application plugin
 * under the name 'application'.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'application'
 * }
 *
 * application {
 *   mainClass.set("com.foo.bar.FooBar")
 * }
 * </pre>
 *
 * @since 4.10
 */
public interface JavaApplication {
    /**
     * The name of the application.
     */
    @ToBeReplacedByLazyProperty
    String getApplicationName();

    /**
     * The name of the application.
     */
    void setApplicationName(String applicationName);

    /**
     * The name of the application's Java module if it should run as a module.
     *
     * @since 6.4
     */
    Property<String> getMainModule();

    /**
     * The fully qualified name of the application's main class.
     *
     * @since 6.4
     */
    Property<String> getMainClass();

    /**
     * Array of string arguments to pass to the JVM when running the application
     */
    @ToBeReplacedByLazyProperty
    Iterable<String> getApplicationDefaultJvmArgs();

    /**
     * Array of string arguments to pass to the JVM when running the application
     */
    void setApplicationDefaultJvmArgs(Iterable<String> applicationDefaultJvmArgs);

    /**
     * Directory to place executables in
     */
    @ToBeReplacedByLazyProperty
    String getExecutableDir();

    /**
     * Directory to place executables in
     */
    void setExecutableDir(String executableDir);

    /**
     * <p>The specification of the contents of the distribution.</p>
     * <p>
     * Use this {@link org.gradle.api.file.CopySpec} to include extra files/resource in the application distribution.
     * <pre class='autoTested'>
     * plugins {
     *     id 'application'
     * }
     *
     * application {
     *     applicationDistribution.from("some/dir") {
     *         include "*.txt"
     *     }
     * }
     * </pre>
     * <p>
     * Note that the application plugin pre configures this spec to; include the contents of "{@code src/dist}",
     * copy the application start scripts into the "{@code bin}" directory, and copy the built jar and its dependencies
     * into the "{@code lib}" directory.
     */
    @ToBeReplacedByLazyProperty
    CopySpec getApplicationDistribution();

    void setApplicationDistribution(CopySpec applicationDistribution);
}
