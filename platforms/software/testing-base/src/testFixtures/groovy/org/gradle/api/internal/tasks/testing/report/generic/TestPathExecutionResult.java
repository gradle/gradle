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

import org.gradle.api.tasks.testing.TestResult;
import org.hamcrest.Matcher;

import java.util.LinkedHashMap;
import java.util.List;

// For now, I think this works enough. It will need to be rewritten to account for different root tabs.
public interface TestPathExecutionResult {
    /**
     * Asserts that the given child paths (and only the given child paths) were executed for the current test path.
     *
     * <p>
     * For example, if you want to know if {@code :TestClass:testMethod} executed {@code subTest1} and {@code subTest2},
     * you would call {@code testPath(":TestClass:testMethod").assertPathsExecuted("subTest1", "subTest2")}.
     * </p>
     *
     * <p>
     * This method only works on direct children of the current test path.
     * </p>
     */
    TestPathExecutionResult assertChildrenExecuted(String... testNames);

    TestPathExecutionResult assertChildCount(int tests, int failures, int errors);

    TestPathExecutionResult assertStdout(Matcher<? super String> matcher);

    TestPathExecutionResult assertStderr(Matcher<? super String> matcher);

    TestPathExecutionResult assertHasResult(TestResult.ResultType resultType);

    TestPathExecutionResult assertFailureMessages(Matcher<? super String> matcher);

    /**
     * Asserts that the given metadata keys are present in the test result.
     *
     * @param keys the keys to verify, in the order they were recorded
     * @return {@code this}
     */
    TestPathExecutionResult assertMetadata(List<String> keys);


    /**
     * Asserts that the given metadata keys are present in the test result with the given rendered text.
     *
     * @param metadata the metadata to verify, in the order they were recorded
     * @return {@code this}
     */
    TestPathExecutionResult assertMetadata(LinkedHashMap<String, String> metadata);
}
