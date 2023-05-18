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

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.integtests.fixtures.TestClassExecutionResult.TestCase
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.Matcher

class DefaultTestExecutionResult implements TestExecutionResult {

    List<TestExecutionResult> results = []
    HtmlTestExecutionResult htmlResult
    JUnitXmlTestExecutionResult xmlResult

    DefaultTestExecutionResult(TestFile projectDir, String buildDirName = 'build', String binary='', String testedBinary = '', String testTaskName = 'test') {
        String binaryPath = binary?"/$binary":''
        binaryPath = testedBinary?"$binaryPath/$testedBinary":"$binaryPath";
        if(binary){
            htmlResult = new HtmlTestExecutionResult(projectDir, "$buildDirName/reports${binaryPath}/tests/")
            xmlResult = new JUnitXmlTestExecutionResult(projectDir, "$buildDirName/test-results${binaryPath}")

            results << htmlResult
            results << xmlResult
        }else{
            htmlResult = new HtmlTestExecutionResult(projectDir, "$buildDirName/reports/tests/${testTaskName}")
            xmlResult = new JUnitXmlTestExecutionResult(projectDir, "$buildDirName/test-results/${testTaskName}")

            results << htmlResult
            results << xmlResult
        }
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        results.each { result ->
            result.assertTestClassesExecuted(testClasses)
        }
        return this
    }

    TestExecutionResult assertTestClassesNotExecuted(String... testClasses) {
        results.each { result ->
            result.assertTestClassesNotExecuted(testClasses)
        }
        return this
    }

    DefaultTestExecutionResult assertTestClassesExecutedJudgementByHtml(String... testClasses) {
        htmlResult.assertTestClassesExecuted(testClasses)
        this
    }

    DefaultTestExecutionResult assertTestClassesExecutedJudgementByXml(String... testClasses) {
        xmlResult.assertTestClassesExecuted(testClasses)
        this
    }

    boolean testClassExists(String testClass) {
        List<Boolean> testClassResults = results*.testClassExists(testClass)
        return testClassResults.inject { a, b -> a && b }
    }

    boolean testClassDoesNotExist(String testClass) {
        return !results || !testClassExists(testClass)
    }

    TestClassExecutionResult testClass(String testClass) {
        new DefaultTestClassExecutionResult(results.collect {it.testClass(testClass)})
    }

    TestClassExecutionResult testClassByHtml(String testClass) {
        htmlResult.testClass(testClass)
    }

    TestClassExecutionResult testClassByXml(String testClass) {
        xmlResult.testClass(testClass)
    }

    TestClassExecutionResult testClassStartsWith(String testClass) {
        new DefaultTestClassExecutionResult(results.collect { it.testClassStartsWith(testClass) })
    }

    void assertNoTestClassesExecuted() {
        assert totalNumberOfTestClassesExecuted == 0
    }

    @Override
    int getTotalNumberOfTestClassesExecuted() {
        assert !results.isEmpty()
        def firstResult = results[0].totalNumberOfTestClassesExecuted
        assert results.every { firstResult == it.totalNumberOfTestClassesExecuted }
        return firstResult
    }

    // In JUnit 3/4 test case name is exactly the method name
    // In JUnit 5 test method injection is allowed: http://junit.org/junit5/docs/current/user-guide/#writing-tests-dependency-injection
    // So test case name is [method name + parameters]
    static String removeParentheses(String testName) {
        testName.size() > 2 && testName.endsWith('()') ? testName[0..-3] : testName
    }

    static String[] removeAllParentheses(String... testNames) {
        testNames.collect { removeParentheses(it) } as String[]
    }

    static String[][] removeAllParentheses(String[]... testNames) {
        testNames.collect { [removeParentheses(it[0]), it[1]] } as String[][]
    }

    static TestCase testCase(String name) {
        return new DefaultTestCase(name)
    }

    static TestCase testCase(String name, String displayName) {
        return new DefaultTestCase(name, displayName)
    }

    static TestCase testCase(String name, String displayName, List<String> messages) {
        return new DefaultTestCase(name, displayName, messages)
    }

    @ToString
    @EqualsAndHashCode(includes = ['name', 'displayName'])
    private static class DefaultTestCase implements TestCase {
        String name
        String displayName
        List<String> messages

        DefaultTestCase(String name) {
            this(name, name)
        }

        DefaultTestCase(String name, String displayName) {
            this(name, displayName, [])
        }

        DefaultTestCase(String name, String displayName, List<String> messages) {
            this.name = removeParentheses(name)
            this.displayName = removeParentheses(displayName)
            this.messages = messages
        }
    }

    private class DefaultTestClassExecutionResult implements TestClassExecutionResult {
        List<TestClassExecutionResult> testClassResults

        private DefaultTestClassExecutionResult(List<TestClassExecutionResult> classExecutionResults) {
            this.testClassResults = classExecutionResults;
        }

        TestClassExecutionResult assertTestsExecuted(String... testNames) {
            testClassResults*.assertTestsExecuted(removeAllParentheses(testNames))
            this
        }

        @Override
        TestClassExecutionResult assertTestsExecuted(TestCase... testCases) {
            testClassResults*.assertTestsExecuted(testCases)
            this
        }

        TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
            testClassResults*.assertTestCount(tests, failures, errors)
            this
        }

        int getTestCount() {
            List<Integer> counts = testClassResults*.testCount
            List<Integer> uniques = counts.unique()
            if (uniques.size() == 1) {
                return uniques.first()
            }
            throw new IllegalStateException("Multiple different test counts ${counts}")
        }

        TestClassExecutionResult assertTestsSkipped(String... testNames) {
            testClassResults*.assertTestsSkipped(removeAllParentheses(testNames))
            this
        }

        @Override
        TestClassExecutionResult assertTestPassed(String name, String displayName) {
            testClassResults*.assertTestPassed(removeParentheses(name), removeParentheses(displayName))
            this
        }

        int getTestSkippedCount() {
            List<Integer> counts = testClassResults*.testSkippedCount
            List<Integer> uniques = counts.unique()
            if (uniques.size() == 1) {
                return uniques.first()
            }
            throw new IllegalStateException("Multiple different test counts ${counts}")
        }

        TestClassExecutionResult assertTestPassed(String name) {
            testClassResults*.assertTestPassed(removeParentheses(name))
            this
        }

        @Override
        TestClassExecutionResult assertTestFailed(String name, String displayName, Matcher<? super String>... messageMatchers) {
            testClassResults*.assertTestFailed(removeParentheses(name), removeParentheses(displayName), messageMatchers)
            this
        }

        TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
            testClassResults*.assertTestFailed(removeParentheses(name), messageMatchers)
            this
        }

        boolean testFailed(String name, Matcher<? super String>... messageMatchers) {
            List<Boolean> results = testClassResults*.testFailed(name, messageMatchers)
            return results.inject { a, b -> a && b }
        }

        @Override
        TestClassExecutionResult assertTestSkipped(String name, String displayName) {
            testClassResults*.assertTestSkipped(removeParentheses(name), removeParentheses(displayName))
            this
        }

        TestClassExecutionResult assertTestSkipped(String name) {
            testClassResults*.assertTestSkipped(removeParentheses(name))
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
            testClassResults*.assertTestCaseStdout(removeParentheses(testCaseName), matcher)
            this
        }

        TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
            testClassResults*.assertStderr(matcher)
            this
        }

        TestClassExecutionResult assertTestCaseStderr(String testCaseName, Matcher<? super String> matcher) {
            testClassResults*.assertTestCaseStderr(removeParentheses(testCaseName), matcher)
            this
        }

        TestClassExecutionResult assertExecutionFailedWithCause(Matcher<? super String> causeMatcher) {
            testClassResults*.assertExecutionFailedWithCause(causeMatcher)
            this
        }

        @Override
        TestClassExecutionResult assertDisplayName(String classDisplayName) {
            testClassResults*.assertDisplayName(classDisplayName)
            this
        }
    }
}
