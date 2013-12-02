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
 * Allows selecting tests for execution
 *
 * @since 1.10
 */
@Incubating
public interface TestFilter {

    /**
     * Appends a test name to the filter. Wildcard '*' is supported.
     * Examples of test names: com.foo.FooTest.someMethod, com.foo.FooTest, *FooTest*, com.foo*
     *
     * @param testName test's name, wildcard '*' is supported.
     * @return this filter object
     */
    TestFilter includeTest(String testName);

    /**
     * Returns the included test names, wildcard '*' is supported.
     *
     * @return included test names, wildcard '*' is supported.
     */
    Set<String> getIncludedTests();

    /**
     * Sets the test names to be included in the filter. Wildcard '*' is supported.
     *
     * @param testNames test names, wildcard '*' is supported.
     * @return this filter object
     */
    TestFilter setIncludedTests(String... testNames);
}
