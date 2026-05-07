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

import com.google.common.collect.ImmutableList;
import org.gradle.api.tasks.testing.TestResult;
import org.hamcrest.Matcher;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of executing a test path in a specific root.
 */
public interface TestPathRootExecutionResult {
    /**
     * Asserts that the given child paths (and only the given child paths) were executed for the current test path.
     *
     * <p>
     * For example, if you want to know if {@code :TestClass:testMethod} executed {@code subTest1} and {@code subTest2},
     * you would call {@code testPath(":TestClass:testMethod").assertChildrenExecuted("subTest1", "subTest2")}.
     * </p>
     *
     * <p>
     * This method only works on direct children of the current test path.
     * </p>
     */
    TestPathRootExecutionResult assertOnlyChildrenExecuted(String... testNames);

    /**
     * Asserts that the given child paths were executed for the current test path.
     * <p>
     * Unlike {@link #assertOnlyChildrenExecuted(String...)}, this method does not fail if other children were executed.
     * <p>
     * This method only works on direct children of the current test path.
     * </p>
     */
    TestPathRootExecutionResult assertChildrenExecuted(String... testNames);

    TestPathRootExecutionResult assertChildCount(int tests, int failures);

    /**
     * Returns the number of child tests that were executed for the current test path.
     *
     * @return the number of executed child tests
     */
    int getExecutedChildCount();

    TestPathRootExecutionResult assertChildrenSkipped(String... testNames);

    /**
     * Returns the number of child tests that were skipped for the current test path.
     *
     * @return the number of skipped child tests
     */
    int getSkippedChildCount();


    TestPathRootExecutionResult assertChildrenFailed(String... testNames);

    /**
     * Returns the number of child tests that were failed for the current test path.
     *
     * @return the number of failed child tests
     */
    int getFailedChildCount();

    TestPathRootExecutionResult assertStdout(Matcher<? super String> matcher);

    TestPathRootExecutionResult assertStderr(Matcher<? super String> matcher);

    TestPathRootExecutionResult assertHasResult(TestResult.ResultType resultType);

    TestPathRootExecutionResult assertDisplayName(Matcher<? super String> matcher);

    TestPathRootExecutionResult assertFailureMessages(Matcher<? super String> matcher);

    /**
     * Asserts that there is only one duration recorded for the test path, and that it matches the given matcher.
     *
     * @param matcher the matcher to verify the duration
     * @return {@code this}
     */
    TestPathRootExecutionResult assertThatSingleDuration(Matcher<? super Duration> matcher);

    String getFailureMessages();

    /**
     * Asserts that the given metadata keys are present in the test result.
     *
     * @param keys the keys to verify, in the order they were recorded
     * @return {@code this}
     */
    TestPathRootExecutionResult assertMetadataKeys(List<String> keys);

    /**
     * Asserts that the given metadata keys are present in the test result with the given rendered text.
     *
     * <p>
     * If you have duplicate keys, use {@link #assertMetadata(List)} instead.
     * </p>
     *
     * @param metadata the metadata to verify, in the order they were recorded
     * @return {@code this}
     */
    default TestPathRootExecutionResult assertMetadata(LinkedHashMap<String, String> metadata) {
        return assertMetadata(ImmutableList.copyOf(metadata.entrySet()));
    }

    /**
     * Asserts that the given metadata keys are present in the test result with the given rendered text.
     *
     * <p>
     * There may be duplicate keys, hence the use of a list of entries.
     * </p>
     *
     * @param metadata the metadata to verify, in the order they were recorded
     * @return {@code this}
     */
    TestPathRootExecutionResult assertMetadata(List<Map.Entry<String, String>> metadata);

    /**
     * Flags to indicate how a file attachment should be rendered
     */
    enum ShowAs {
        LINK, IMAGE, VIDEO;
    }

    /**
     * Asserts that the given file attachments are shown as the given type in the report.
     *
     * @param expectedAttachments the expected file attachments
     * @return {@code this}
     */
    TestPathRootExecutionResult assertFileAttachments(Map<String, ShowAs> expectedAttachments);
}
