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

import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.Matcher


class DefaultTestExecutionResult implements TestExecutionResult {

    def results = []

    public DefaultTestExecutionResult(TestFile projectDir, String buildDirName = 'build') {
        results << new HtmlTestExecutionResult(projectDir, "$buildDirName/reports/tests")
        results << new JUnitXmlTestExecutionResult(projectDir, buildDirName)
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        results.each { result ->
            result.assertTestClassesExecuted(testClasses)
        }
        this
    }

    TestClassExecutionResult testClass(String testClass) {
        new DefaultTestClassExecutionResult(results.collect {it.testClass(testClass)});
    }

    private class DefaultTestClassExecutionResult implements TestClassExecutionResult {
        def testClassResults

        private DefaultTestClassExecutionResult(def classExecutionResults) {
            this.testClassResults = classExecutionResults;
        }

        TestClassExecutionResult assertTestsExecuted(String... testNames) {
            testClassResults*.assertTestsExecuted(testNames)
            this
        }

        TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
            testClassResults*.assertTestCount(tests, failures, errors)
            this
        }

        TestClassExecutionResult assertTestsSkipped(String... testNames) {
            testClassResults*.assertTestsSkipped(testNames)
            this
        }

        TestClassExecutionResult assertTestPassed(String name) {
            testClassResults*.assertTestPassed(name)
            this
        }

        TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
            testClassResults*.assertTestFailed(name, messageMatchers)
            this
        }

        TestClassExecutionResult assertTestSkipped(String name) {
            testClassResults*.assertTestSkipped(name)
            this
        }

        TestClassExecutionResult assertConfigMethodPassed(String name) {
            testClassResults*.assertConfigMethodPassed(name)
            this
        }

        TestClassExecutionResult assertConfigMethodFailed(String name) {
            testClassResults*.assertConfigMethodFailed(name)
            this
        }

        TestClassExecutionResult assertStdout(Matcher<? super String> matcher) {
            testClassResults*.assertStdout(matcher)
            this
        }

        TestClassExecutionResult assertTestCaseStdout(String testCaseName, Matcher<? super String> matcher) {
            testClassResults*.assertTestCaseStdout(testCaseName, matcher)
            this
        }

        TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
            testClassResults*.assertStderr(matcher)
            this
        }

        TestClassExecutionResult assertTestCaseStderr(String testCaseName, Matcher<? super String> matcher) {
            testClassResults*.assertTestCaseStderr(testCaseName, matcher)
            this
        }
    }
}
