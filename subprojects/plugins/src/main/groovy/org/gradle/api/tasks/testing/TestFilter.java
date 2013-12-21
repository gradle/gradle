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

import org.gradle.api.Incubating;

import java.util.Set;

/**
 * Allows filtering tests for execution. Some examples:
 *
 * <pre autoTested=''>
 *   apply plugin: 'java'
 *
 *   test {
 *       filter {
 *          //specific test method
 *          includeTestsMatching "org.gradle.SomeTest.someSpecificFeature"
 *
 *          //specific test method, use wildcard for packages
 *          includeTestsMatching "*SomeTest.someSpecificFeature"
 *
 *          //specific test class
 *          includeTestsMatching "org.gradle.SomeTest"
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
@Incubating
public interface TestFilter {

    /**
     * Appends a test name pattern to the filter. Wildcard '*' is supported,
     * either test method name or class name is supported.
     * Examples of test names: "com.foo.FooTest.someMethod", "com.foo.FooTest", "*FooTest*", "com.foo*".
     * See examples in the docs for {@link TestFilter}.
     *
     * @param testNamePattern test name pattern to include, can be class or method name, can contain wildcard '*'
     * @return this filter object
     */
    TestFilter includeTestsMatching(String testNamePattern);

    /**
     * Returns the included test name patterns. They can be class or method names and may contain wildcard '*'.
     * Test name patterns can be appended via {@link #includeTestsMatching(String)} or set via {@link #setIncludePatterns(String...)}.
     *
     * @return included test name patterns
     */
    Set<String> getIncludePatterns();

    /**
     * Sets the test name patterns to be included in the filter. Wildcard '*' is supported.
     * Replaces any existing test name patterns.
     *
     * @param testNamePatterns class or method name patterns to set, may contain wildcard '*'
     * @return this filter object
     */
    TestFilter setIncludePatterns(String... testNamePatterns);
}
