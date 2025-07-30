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

package org.gradle.api.internal.tasks.testing.report.generic;

import java.util.List;

public interface GenericTestExecutionResult {
    String EXECUTION_FAILURE = "failed to execute tests";

    /**
     * Asserts that the given test paths (and only the given test paths) were executed.
     *
     * <p>
     * These are paths in the style of {@link org.gradle.util.Path}, e.g. `:TestClass:testMethod:subTest`.
     * </p>
     */
    GenericTestExecutionResult assertTestPathsExecuted(String... testPaths);

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
