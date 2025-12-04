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

interface GenericTestExecutionResult {
    /**
     * Asserts that only test paths that match any of the given selectors or their ancestors were executed.
     * Also asserts that all given selectors match at least one executed test path.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * The full syntax is described at {@link TestPathSelector}.
     * </p>
     */
    GenericTestExecutionResult assertTestPathsExecuted(String... testPathSelectors);

    /**
     * Asserts that all selectors match at least one executed test path.
     * There may exist executed test paths that do not match any of the given selectors.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * The full syntax is described at {@link TestPathSelector}.
     * </p>
     */
    GenericTestExecutionResult assertAtLeastTestPathsExecuted(String... testPathSelectors);

    /**
     * Asserts that the selectors do not match any executed test path.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * The full syntax is described at {@link TestPathSelector}.
     * </p>
     */
    GenericTestExecutionResult assertTestPathsNotExecuted(String... testPathSelectors);

    /**
     * Returns the result for the given test path selector, if it is unique.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * The full syntax is described at {@link TestPathSelector}.
     * </p>
     */
    TestPathExecutionResult testPath(String testPathSelector);

    /**
     * Returns the result for the given test path selector, constructed from the given elements,
     * if it is unique.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * The full syntax is described at {@link TestPathSelector}.
     * </p>
     *
     * @param testPathSelectors the elements of the path selector
     * @return the test path execution result
     */
    TestPathExecutionResult testPath(String... testPathElements);

    /**
     * Checks if the selector matches any executed test path.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * The full syntax is described at {@link TestPathSelector}.
     * </p>
     *
     * @return {@code true} if the test path selector matches, {@code false} otherwise.
     */
    boolean testPathExists(String testPathSelector);

    /**
     * Asserts that the given metadata keys are present in the suite summary.
     *
     * @param keys the keys to verify, in the order they were recorded
     * @return {@code this}
     */
    GenericTestExecutionResult assertMetadata(List<String> keys);
}
