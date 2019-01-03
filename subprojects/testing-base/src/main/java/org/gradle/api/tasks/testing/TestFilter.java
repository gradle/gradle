/*
 * Copyright 2013 the original author or authors.
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

import java.util.Set;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;

/**
 * Allows filtering tests for execution. Some examples:
 *
 * <pre class='autoTested'>
 *   apply plugin: 'java'
 *
 *   test {
 *       filter {
 *          //specific test class, this can match 'SomeTest' class and corresponding method under any package
 *          includeTestsMatching "SomeTest"
 *          includeTestsMatching "SomeTest.someTestMethod*"
 *
 *          //specific test class
 *          includeTestsMatching "org.gradle.SomeTest"
 *
 *          //specific test class and method
 *          includeTestsMatching "org.gradle.SomeTest.someSpecificFeature"
 *          includeTest "org.gradle.SomeTest", "someTestMethod"
 *
 *          //specific test method, use wildcard
 *          includeTestsMatching "*SomeTest.someSpecificFeature"
 *
 *          //specific test class, wildcard for packages
 *          includeTestsMatching "*.SomeTest"
 *
 *          //all classes in package, recursively
 *          includeTestsMatching "com.gradle.tooling.*"
 *
 *          //all integration tests, by naming convention
 *          includeTestsMatching "*IntegTest"
 *
 *          //only ui tests from integration tests, by some naming convention
 *          includeTestsMatching "*IntegTest*ui"
 *       }
 *   }
 *
 * </pre>
 *
 * @since 1.10
 */
public interface TestFilter {

    /**
     * Appends a test name pattern to the inclusion filter. Wildcard '*' is supported, either test method name or class name is supported. Examples of test names: "com.foo.FooTest.someMethod",
     * "com.foo.FooTest", "*FooTest*", "com.foo*". See examples in the docs for {@link TestFilter}.
     *
     * @param testNamePattern test name pattern to include, can be class or method name, can contain wildcard '*'
     * @return this filter object
     */
    TestFilter includeTestsMatching(String testNamePattern);

    /**
     * Appends a test name pattern to the exclusion filter. Wildcard '*' is supported, either test
     * method name or class name is supported. Examples of test names: "com.foo.FooTest.someMethod",
     * "com.foo.FooTest", "*FooTest*", "com.foo*". See examples in the docs for {@link TestFilter}.
     *
     * @param testNamePattern test name pattern to exclude, can be class or method name, can contain wildcard '*'
     * @return this filter object
     * @since 5.0
     */
    @Incubating
    TestFilter excludeTestsMatching(String testNamePattern);

    /**
     * Returns the included test name patterns. They can be class or method names and may contain wildcard '*'. Test name patterns can be appended via {@link #includeTestsMatching(String)} or set via
     * {@link #setIncludePatterns(String...)}.
     *
     * @return included test name patterns
     */
    @Input
    Set<String> getIncludePatterns();

    /**
     * Returns the excluded test name patterns. They can be class or method names and may contain wildcard '*'.
     * Test name patterns can be appended via {@link #excludeTestsMatching(String)} or set via
     * {@link #setExcludePatterns(String...)}.
     *
     * @return included test name patterns
     * @since 5.0
     */
    @Input
    @Incubating
    Set<String> getExcludePatterns();

    /**
     * Sets the test name patterns to be included in the filter. Wildcard '*' is supported. Replaces any existing test name patterns.
     *
     * @param testNamePatterns class or method name patterns to set, may contain wildcard '*'
     * @return this filter object
     */
    TestFilter setIncludePatterns(String... testNamePatterns);

    /**
     * Sets the test name patterns to be excluded in the filter. Wildcard '*' is supported. Replaces any existing test name patterns.
     *
     * @param testNamePatterns class or method name patterns to set, may contain wildcard '*'
     * @return this filter object
     * @since 5.0
     */
    @Incubating
    TestFilter setExcludePatterns(String... testNamePatterns);

    /**
     * Add a test method specified by test class name and method name.
     *
     * @param className the class name of the test to execute
     * @param methodName the method name of the test to execute. Can be null.
     * @return this filter object
     */
    TestFilter includeTest(String className, String methodName);

    /**
     * Excludes a test method specified by test class name and method name.
     *
     * @param className the class name of the test to exclude
     * @param methodName the method name of the test to exclude. Can be null.
     * @return this filter object
     * @since 5.0
     */
    @Incubating
    TestFilter excludeTest(String className, String methodName);

    /**
     * Let the test task fail if a filter configuration was provided but no test matched the given configuration.
     * @param failOnNoMatchingTests whether a test task should fail if no test is matching the filter configuration.
     * */
    void setFailOnNoMatchingTests(boolean failOnNoMatchingTests);

    /**
     * Returns whether the task should fail if no matching tests where found.
     * The default is true.
     */
    @Input
    boolean isFailOnNoMatchingTests();
}

