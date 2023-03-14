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

package org.gradle.integtests.fixtures;

import org.hamcrest.Matcher;

import java.util.List;

public interface TestClassExecutionResult {
    /**
     * Asserts that the given tests (and only the given tests) were executed for the given test class.
     */
    TestClassExecutionResult assertTestsExecuted(String... testNames);

    /**
     * Asserts that the given tests (and only the given tests) were executed for the given test class.
     *
     * This supports JUnit5 parameterized tests where the test name and display name may not match.
     */
    TestClassExecutionResult assertTestsExecuted(TestCase... testCases);

    TestClassExecutionResult assertTestCount(int tests, int failures, int errors);

    int getTestCount();

    /**
     * Asserts that the given tests (and only the given tests) were skipped for the given test class.
     */
    TestClassExecutionResult assertTestsSkipped(String... testNames);

    int getTestSkippedCount();

    /**
     * Asserts that the given test passed.
     */
    TestClassExecutionResult assertTestPassed(String name, String displayName);

    TestClassExecutionResult assertTestPassed(String name);

    /**
     * Asserts that the given test failed.
     */
    TestClassExecutionResult assertTestFailed(String name, String displayName, Matcher<? super String>... messageMatchers);

    TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers);
    /**
     *
     */
    boolean testFailed(String name, Matcher<? super String>... messageMatchers);

    /**
     * Asserts that the given test was skipped.
     */
    TestClassExecutionResult assertTestSkipped(String name, String displayName);

    TestClassExecutionResult assertTestSkipped(String name);

    /**
     * Asserts that the given config method passed.
     */
    TestClassExecutionResult assertConfigMethodPassed(String name);

    /**
     * Asserts that the given config method failed.
     */
    TestClassExecutionResult assertConfigMethodFailed(String name);

    TestClassExecutionResult assertStdout(Matcher<? super String> matcher);

    TestClassExecutionResult assertTestCaseStdout(String testCaseName, Matcher<? super String> matcher);

    TestClassExecutionResult assertStderr(Matcher<? super String> matcher);

    TestClassExecutionResult assertTestCaseStderr(String testCaseName, Matcher<? super String> matcher);

    TestClassExecutionResult assertExecutionFailedWithCause(Matcher<? super String> causeMatcher);

    TestClassExecutionResult assertDisplayName(String classDisplayName);

    interface TestCase {
        String getName();
        String getDisplayName();
        List<String> getMessages();
    }
}
