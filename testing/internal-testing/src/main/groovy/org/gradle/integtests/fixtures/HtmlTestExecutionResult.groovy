/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import org.gradle.api.internal.tasks.testing.report.generic.TestPathExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.TestPathRootExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.SafeFileLocationUtils
import org.gradle.util.Path
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

import java.util.function.Consumer

import static org.gradle.integtests.fixtures.DefaultTestExecutionResult.testCase
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.anyOf
import static org.hamcrest.Matchers.blankOrNullString
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasItems
import static org.hamcrest.Matchers.not

/**
 * Adapts GenericHtmlTestExecutionResult to be used with the Test task, which assumes a specific HTML report structure.
 */
class HtmlTestExecutionResult implements TestExecutionResult {

    private static boolean isTestClass(Path path) {
        return path.segmentCount() == 1
    }

    private final GenericHtmlTestExecutionResult delegate

    HtmlTestExecutionResult(File projectDirectory, String testReportDirectory = "build/reports/tests/test", TestFramework testFramework = TestFramework.JUNIT_JUPITER) {
        this.delegate = new GenericHtmlTestExecutionResult(projectDirectory, testReportDirectory, testFramework)
    }

    private Set<String> getExecutedTestClasses() {
        return delegate.executedTestPaths.findAll { isTestClass(it) }
            .collect { it.name }
            .toSet()
    }

    /**
     * Assert that the elements returned by the CSS query are what would be expected.
     *
     * Assertions are performed in the given action.
     */
    TestExecutionResult assertHtml(String cssQuery, Action<Collection<?>> action) {
        delegate.assertHtml(cssQuery, action)
        return this
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        assertThat(executedTestClasses, equalTo(testClasses.toList().toSet()))
        return this
    }

    TestExecutionResult assertTestClassesNotExecuted(String... testClasses) {
        assertThat(executedTestClasses, not(hasItems(testClasses)))
        return this
    }

    boolean testClassExists(String testClass) {
        return delegate.testPathExists(testClass)
    }

    boolean testClassDoesNotExist(String testClass) {
        return !testClassExists(testClass)
    }

    TestClassExecutionResult testClass(String testClass) {
        return new HtmlTestClassExecutionResult(delegate, testClass, delegate.testPath(testClass))
    }

    TestClassExecutionResult testClassStartsWith(String testClass) {
        def testClassDelegate = delegate.executedTestPaths.find { isTestClass(it) && it.segment(1).startsWith(SafeFileLocationUtils.toSafeFileName(testClass, false)) }
        assert testClassDelegate, "No HTML file found for test class starting with '${testClass}'"
        return this.testClass(testClassDelegate.name)
    }

    @Override
    int getTotalNumberOfTestClassesExecuted() {
        return delegate.executedTestPaths.count { isTestClass(it) }
    }

    private static class HtmlTestClassExecutionResult implements TestClassExecutionResult {
        private final GenericHtmlTestExecutionResult parentDelegate
        private final String pathToTestClass
        private final TestPathExecutionResult delegate

        HtmlTestClassExecutionResult(GenericHtmlTestExecutionResult parentDelegate, String pathToTestClass, TestPathExecutionResult delegate) {
            this.delegate = delegate
            this.parentDelegate = parentDelegate
            this.pathToTestClass = pathToTestClass
        }

        private TestPathRootExecutionResult getTest(String name) {
            parentDelegate.testPath(pathToTestClass + ":" + name).onlyRoot()
        }

        private TestPathRootExecutionResult getTestWithDisplayName(String name, String displayName) {
            getTest(name).assertDisplayName(equalTo(displayName))
        }

        TestClassExecutionResult assertTestsExecuted(String... testNames) {
            return assertTestsExecuted(testNames.collect { testCase(it) } as TestCase[])
        }

        @Override
        TestClassExecutionResult assertTestsExecuted(TestCase... testCases) {
            String[] testCasesAsNames = testCases.collect { it.name }.toArray(new String[0])
            delegate.onlyRoot().assertOnlyChildrenExecuted(testCasesAsNames)
            return this
        }

        TestClassExecutionResult assertTestCount(int tests, int failures) {
            delegate.onlyRoot().assertChildCount(tests, failures)
            return this
        }

        int getTestCount() {
            return delegate.onlyRoot().executedChildCount
        }

        TestClassExecutionResult assertTestsSkipped(String... testNames) {
            delegate.onlyRoot().assertChildrenSkipped(testNames)
            return this
        }

        @Override
        TestClassExecutionResult assertTestPassed(String name, String displayName) {
            getTestWithDisplayName(name, displayName).assertHasResult(TestResult.ResultType.SUCCESS)
            return this
        }

        int getTestSkippedCount() {
            return delegate.onlyRoot().skippedChildCount
        }

        TestClassExecutionResult assertTestPassed(String name) {
            getTest(name).assertHasResult(TestResult.ResultType.SUCCESS)
            return this
        }

        @Override
        TestClassExecutionResult assertTestFailed(String name, String displayName, Matcher<? super String>... messageMatchers) {
            assert testFailed(name, displayName, messageMatchers)
            return this
        }

        @Override
        TestClassExecutionResult assertTestFailedIgnoreMessages(String name) {
            assertTestFailFullMessages(name, name, not(blankOrNullString()))
            return this
        }

        TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
            assert testFailed(name, messageMatchers)
            return this
        }

        boolean testFailed(String name, Matcher<? super String>... messageMatchers) {
            return testFailed(name, name, messageMatchers)
        }

        boolean testFailed(String name, String displayName, Matcher<? super String>... messageMatchers) {
            def messageLinesMatcher = new BaseMatcher<String>() {
                @Override
                boolean matches(Object actual) {
                    String str = actual as String

                    def messages = str.readLines()
                    if (messages.size() != messageMatchers.length) {
                        return false
                    }
                    for (int i = 0; i < messageMatchers.length; i++) {
                        if (!messageMatchers[i].matches(messages[i])) {
                            return false
                        }
                    }
                    return true
                }

                @Override
                void describeTo(Description description) {
                    description.appendList(
                        "a String with lines matching ",
                        ", ",
                        " in order",
                        messageMatchers.toList()
                    )
                }
            }
            def fullMatcher = messageMatchers.size() == 1
                ? anyOf(messageMatchers[0], messageLinesMatcher)
                : messageLinesMatcher
            assertTestFailFullMessages(name, displayName, fullMatcher)
            return true
        }

        private void assertTestFailFullMessages(String name, String displayName, Matcher<? extends String> matcher) {
            getTestWithDisplayName(name, displayName)
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(matcher)
        }

        @Override
        TestClassExecutionResult assertTestSkipped(String name, String displayName) {
            getTestWithDisplayName(name, displayName).assertHasResult(TestResult.ResultType.SKIPPED)
            return this
        }

        @Override
        TestClassExecutionResult assertTestSkipped(String name, Consumer<SkippedExecutionResult> assertions) {
            String messages = getTest(name).failureMessages

            final SkippedExecutionResult skippedExecutionResult
            if (!messages.isEmpty()) {
                List<String> possibleText = messages.readLines()
                // In the HTML report, the format is:
                // message
                // <newline>
                // exception stacktrace
                String message = possibleText[0]
                String exceptionType = possibleText[2].split(":")[0]
                skippedExecutionResult = new SkippedExecutionResult(message, exceptionType, possibleText[2,-1].join("\n"))
            } else {
                // no messages for this skipped execution
                skippedExecutionResult = new SkippedExecutionResult("", "", "")
            }
            assertions.accept(skippedExecutionResult)
            return this
        }

        TestClassExecutionResult assertExecutionFailedWithCause(Matcher<? super String> causeMatcher) {
            getTest(EXECUTION_FAILURE)
                .assertHasResult(TestResult.ResultType.FAILURE)
                .assertFailureMessages(new BaseMatcher<String>() {
                    @Override
                    boolean matches(Object actual) {
                        String causeLinePrefix = "Caused by: "
                        def cause = (actual as String).readLines()
                            .find { it.startsWith(causeLinePrefix) }
                            ?.substring(causeLinePrefix.length())
                        return causeMatcher.matches(cause)
                    }

                    @Override
                    void describeTo(Description description) {
                        description.appendText("an execution failure with cause matching ")
                            .appendDescriptionOf(causeMatcher)
                    }
                })
            this
        }

        @Override
        TestClassExecutionResult assertDisplayName(String classDisplayName) {
            assert classDisplayName == classDisplayName
            this
        }

        TestClassExecutionResult assertTestSkipped(String name) {
            getTest(name).assertHasResult(TestResult.ResultType.SKIPPED)
            return this
        }

        TestClassExecutionResult assertConfigMethodPassed(String name) {
            return null
        }

        TestClassExecutionResult assertConfigMethodFailed(String name) {
            return null
        }

        TestClassExecutionResult assertStdout(Matcher<? super String> matcher) {
            delegate.onlyRoot().assertStdout(matcher)
            return this
        }

        TestClassExecutionResult assertTestCaseStdout(String testCaseName, Matcher<? super String> matcher) {
            getTest(testCaseName).assertStdout(matcher)
            return this
        }

        TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
            delegate.onlyRoot().assertStderr(matcher)
            return this
        }

        TestClassExecutionResult assertTestCaseStderr(String testCaseName, Matcher<? super String> matcher) {
            getTest(testCaseName).assertStderr(matcher)
            return this
        }
    }
}
