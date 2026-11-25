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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.util.FilePatterns;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link Test}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface TestInputs extends AbstractTestTaskInputs {

    /**
     * The directories with the compiled test classes.
     *
     * @see Test#getTestClassesDirs()
     * @since 10.0.0
     */
    ConfigurableFileCollection getTestClassesDirs();

    /**
     * The classpath used to execute the tests.
     *
     * @see Test#getClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getClasspath();

    /**
     * Whether test classes are detected by scanning.
     *
     * @see Test#isScanForTestClasses()
     * @since 10.0.0
     */
    Property<Boolean> getScanForTestClasses();

    /**
     * The maximum number of test classes to execute in a forked test process.
     *
     * @see Test#getForkEvery()
     * @since 10.0.0
     */
    Property<Long> getForkEvery();

    /**
     * The maximum number of test processes to start in parallel.
     *
     * @see Test#getMaxParallelForks()
     * @since 10.0.0
     */
    Property<Integer> getMaxParallelForks();

    /**
     * The extra arguments of the test process, without the arguments that have a dedicated property.
     *
     * @see Test#getJvmArgs()
     * @since 10.0.0
     */
    ListProperty<String> getJvmArgs();

    /**
     * The system properties of the test process.
     *
     * @see Test#getSystemProperties()
     * @since 10.0.0
     */
    MapProperty<String, Object> getSystemProperties();

    /**
     * The minimum heap size of the test process.
     *
     * @see Test#getMinHeapSize()
     * @since 10.0.0
     */
    Property<String> getMinHeapSize();

    /**
     * The maximum heap size of the test process.
     *
     * @see Test#getMaxHeapSize()
     * @since 10.0.0
     */
    Property<String> getMaxHeapSize();

    /**
     * Whether assertions are enabled in the test process.
     *
     * @see Test#getEnableAssertions()
     * @since 10.0.0
     */
    Property<Boolean> getEnableAssertions();

    /**
     * The default character encoding of the test process.
     *
     * @see Test#getDefaultCharacterEncoding()
     * @since 10.0.0
     */
    Property<String> getDefaultCharacterEncoding();

    /**
     * The bootstrap classpath of the test process.
     *
     * @see Test#getBootstrapClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getBootstrapClasspath();

    /**
     * The executable of the test process.
     *
     * @see Test#getExecutable()
     * @since 10.0.0
     */
    Property<String> getExecutable();

    /**
     * The environment variables of the test process.
     *
     * @see Test#getEnvironment()
     * @since 10.0.0
     */
    MapProperty<String, Object> getEnvironment();

    /**
     * The include and exclude patterns that select the test class files in the test classes directories.
     *
     * <p>The eager pattern methods of the task, such as {@link Test#include(String...)}, change this value.</p>
     *
     * @see Test#getIncludes()
     * @see Test#getExcludes()
     * @since 10.0.0
     */
    Property<FilePatterns> getClassFilePatterns();
}
