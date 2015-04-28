/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling;

import org.gradle.api.Incubating;

/**
 * A test launcher allows execution of tests from the tooling api, by defining include/exclude filters.
 *
 * @since 2.5
 */
@Incubating
public interface TestsLauncher extends ConfigurableLauncher {
    /**
     * Adds a selection of tests to be executed thanks to the provided patterns.
     *
     * @param patterns patterns of tests to be included
     *
     * @return this instance
     */
    TestsLauncher addTestsByPattern(String... patterns);

    /**
     * Adds test classes to the list of tests to be executed. The name of the class
     * is a fully qualified class name.
     *
     * @param testClasses some test classes to be added to the test execution.
     *
     * @return this instance
     */
    TestsLauncher addJvmTestClasses(String... testClasses);

    /**
     * Adds test methods from a class to be executed.
     *
     * @param testClass the test class which includes some test methods to be included
     * @param methods the names of the test methods to be included
     *
     * @return this instance
     */
    TestsLauncher addJvmTestMethods(String testClass, String... methods);

    /**
     * Adds a selection of tests to be excluded based on the provided patterns.
     *
     * @param patterns patterns of tests to be excluded
     *
     * @return this instance
     */
    TestsLauncher excludeTestsByPattern(String... patterns);

    /**
     * Excludes test classes from the list of tests to be executed. The name of the class
     * is a fully qualified class name.
     *
     * @param testClasses some test classes to be excluded from test execution.
     *
     * @return this instance
     */
    TestsLauncher excludeJvmTestClasses(String... testClasses);

    /**
     * Excludes test methods from a class to be executed.
     *
     * @param testClass the test class which includes some test methods to be excluded
     * @param methods the names of the test methods to be excluded
     *
     * @return this instance
     */
    TestsLauncher excludeJvmTestMethods(String testClass, String... methods);

    /**
     * Indicates that tests have to be executed even if the underlying test task is up-to-date. Defaults to false.
     *
     * @param alwaysRun set this to true if you want tests to be executed independently of the up-to-date status of the task
     *
     * @return this instance
     */
    TestsLauncher setAlwaysRunTests(boolean alwaysRun);
}
