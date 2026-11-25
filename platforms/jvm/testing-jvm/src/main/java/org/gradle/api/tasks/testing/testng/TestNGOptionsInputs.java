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

package org.gradle.api.tasks.testing.testng;

import org.gradle.api.Incubating;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * The lazy properties behind the eager getters and setters of {@link TestNGOptions}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface TestNGOptionsInputs {

    /**
     * The location to write TestNG's output.
     *
     * @see TestNGOptions#getOutputDirectory()
     * @since 10.0.0
     */
    DirectoryProperty getOutputDirectory();

    /**
     * The groups to include.
     *
     * @see TestNGOptions#getIncludeGroups()
     * @since 10.0.0
     */
    SetProperty<String> getIncludeGroups();

    /**
     * The groups to exclude.
     *
     * @see TestNGOptions#getExcludeGroups()
     * @since 10.0.0
     */
    SetProperty<String> getExcludeGroups();

    /**
     * The behavior when a configuration method fails.
     *
     * @see TestNGOptions#getConfigFailurePolicy()
     * @since 10.0.0
     */
    Property<String> getConfigFailurePolicy();

    /**
     * The fully qualified class names of the listeners to add to TestNG.
     *
     * @see TestNGOptions#getListeners()
     * @since 10.0.0
     */
    SetProperty<String> getListeners();

    /**
     * The parallel mode to use for running the tests.
     *
     * @see TestNGOptions#getParallel()
     * @since 10.0.0
     */
    Property<String> getParallel();

    /**
     * The number of threads to use for this run.
     *
     * @see TestNGOptions#getThreadCount()
     * @since 10.0.0
     */
    Property<Integer> getThreadCount();

    /**
     * Whether the default listeners and reporters are used.
     *
     * @see TestNGOptions#getUseDefaultListeners()
     * @since 10.0.0
     */
    Property<Boolean> getUseDefaultListeners();

    /**
     * The fully qualified class name of the thread pool executor factory.
     *
     * @see TestNGOptions#getThreadPoolFactoryClass()
     * @since 10.0.0
     */
    Property<String> getThreadPoolFactoryClass();

    /**
     * The default suite name.
     *
     * @see TestNGOptions#getSuiteName()
     * @since 10.0.0
     */
    Property<String> getSuiteName();

    /**
     * The default test name.
     *
     * @see TestNGOptions#getTestName()
     * @since 10.0.0
     */
    Property<String> getTestName();

    /**
     * The suite XML files to use for running TestNG tests.
     *
     * @see TestNGOptions#getSuiteXmlFiles()
     * @since 10.0.0
     */
    ListProperty<File> getSuiteXmlFiles();

    /**
     * Whether the test classes run in the order of the suite.
     *
     * @see TestNGOptions#getPreserveOrder()
     * @since 10.0.0
     */
    Property<Boolean> getPreserveOrder();

    /**
     * Whether the test methods are grouped by instance.
     *
     * @see TestNGOptions#getGroupByInstances()
     * @since 10.0.0
     */
    Property<Boolean> getGroupByInstances();
}
