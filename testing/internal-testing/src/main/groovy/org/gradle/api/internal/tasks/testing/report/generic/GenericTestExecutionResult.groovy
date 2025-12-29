/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic

import java.util.regex.Pattern

interface GenericTestExecutionResult {
    /**
     * Asserts that the given test paths (and only the given test paths) were executed.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * </p>
     */
    GenericTestExecutionResult assertTestPathsExecuted(String... testPaths);

    /**
     * Asserts that the given test paths were executed. Others may also have been executed.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * </p>
     */
    GenericTestExecutionResult assertAtLeastTestPathsExecuted(String... testPaths);

    /**
     * Asserts that the given test paths (and only the given test paths) were <strong>NOT</strong> executed.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * </p>
     */
    GenericTestExecutionResult assertTestPathsNotExecuted(String... testPaths);

    /**
     * Returns the result for the given test path.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * </p>
     */
    TestPathExecutionResult testPath(String testPath);

    /**
     * Returns the result for the given test class and method.
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `e1:e2:e3...`.
     *
     * @param testPathElements the elements of the path to be concatenated with `:` as separator
     * @return the complete path for the given test path elements
     */
    TestPathExecutionResult testPath(String... testPathElements);

    enum TestFramework {
        CUCUMBER,
        TEST_NG,
        JUNIT_JUPITER,
        SPOCK,
        JUNIT4,
        KOTLIN_TEST,
        SCALA_TEST,
        XC_TEST,
        SPEK,
        CUSTOM,
        ;

        private static final Pattern BASIC_PARAMS_PATTERN = Pattern.compile("\\(.*\\)");

        /**
         * Given the name of a test method, returns the name of the test case that would be reported by this test
         * framework.
         *
         * <p>
         * For example, JUnit 4 uses the method name as the test case name, whereas JUnit Jupiter appends {@code ()}
         * to the method name.
         * </p>
         *
         * @param testMethodName the name of the test method
         * @return the name of the test case as reported by this test framework
         */
        String getTestCaseName(String testMethodName) {
            return switch (this) {
                case JUNIT_JUPITER, KOTLIN_TEST -> {
                    // Don't add "()" if the method name already appears to have parameters
                    if (testMethodName =~ BASIC_PARAMS_PATTERN) {
                        yield testMethodName
                    } else {
                        yield testMethodName + "()"
                    }
                }
                default -> testMethodName;
            };
        }
    }

    /**
     * Checks if the given test path exists.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * </p>
     *
     * @return {@code true} if the test path exists, {@code false} otherwise.
     */
    boolean testPathExists(String testPath);

    /**
     * Asserts that the given metadata keys are present in the suite summary.
     *
     * @param keys the keys to verify, in the order they were recorded
     * @return {@code this}
     */
    GenericTestExecutionResult assertMetadata(List<String> keys);
}
