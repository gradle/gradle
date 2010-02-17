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

package org.gradle.integtests;

public interface TestResult {
    /**
     * Asserts that the given test classes (and only the given test classes) were executed.
     */
    TestResult assertTestClassesExecuted(String... testClasses);

    /**
     * Asserts that the given tests (and only the given tests) were executed for the given test class.
     */
    TestResult assertTestsExecuted(String testClass, String... testNames);

    /**
     * Asserts that the given test passed.
     */
    TestResult assertTestPassed(String testClass, String name);

    /**
     * Asserts that the given test failed.
     */
    TestResult assertTestFailed(String testClass, String name);

    /**
     * Asserts that the given config method passed.
     */
    TestResult assertConfigMethodPassed(String testClass, String name);

    /**
     * Asserts that the given config method failed.
     */
    TestResult assertConfigMethodFailed(String testClass, String name);
}
