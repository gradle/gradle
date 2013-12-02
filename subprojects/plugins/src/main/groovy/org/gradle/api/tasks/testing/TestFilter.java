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
 * Allows filtering tests for execution
 *
 * @since 1.10
 */
@Incubating
public interface TestFilter {

    /**
     * Appends a test name to the filter. Wildcard '*' is supported,
     * either test method name or class name is supported.
     * Examples of test names: com.foo.FooTest.someMethod, com.foo.FooTest, *FooTest*, com.foo*
     *
     * @param testNamePattern test's name to include, can be class or method name, can contain wildcard '*'
     * @return this filter object
     */
    TestFilter includeTestsMatching(String testNamePattern);

    /**
     * Returns the included test names. They can be class or method names and may contain wildcard '*'.
     *
     * @return included test names
     */
    Set<String> getIncludePatterns();

    /**
     * Sets the test names to be included in the filter. Wildcard '*' is supported.
     *
     * @param testNamePatterns class or method names to set, may contain wildcard '*'
     * @return this filter object
     */
    TestFilter setIncludePatterns(String... testNamePatterns);
}
