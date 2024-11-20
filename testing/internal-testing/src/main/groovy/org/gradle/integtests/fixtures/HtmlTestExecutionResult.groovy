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

import org.gradle.internal.FileUtils
import org.gradle.util.internal.TextUtil
import org.hamcrest.Matcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import static org.gradle.integtests.fixtures.DefaultTestExecutionResult.testCase
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

class HtmlTestExecutionResult implements TestExecutionResult {

    private File htmlReportDirectory

    public HtmlTestExecutionResult(File projectDirectory, String testReportDirectory = "build/reports/tests/test") {
        this.htmlReportDirectory = new File(projectDirectory, testReportDirectory);
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        indexContainsTestClass(testClasses)
        assertHtmlReportForTestClassExists(testClasses)
        return this
    }

    TestExecutionResult assertTestClassesNotExecuted(String... testClasses) {
        def indexFile = new File(htmlReportDirectory, "index.html")
        if (indexExists()) {
            List<String> executedTestClasses = getExecutedTestClasses()
            assertThat(executedTestClasses, not(hasItems(testClasses)))
        }
        return this
    }

    boolean indexExists() {
        indexFile.exists()
    }

    private void indexContainsTestClass(String... expectedTestClasses) {
        List<String> executedTestClasses = getExecutedTestClasses()
        assert executedTestClasses.containsAll(expectedTestClasses)
    }

    private List<String> getExecutedTestClasses() {
        File indexFile = getIndexFile()
        assert indexExists()
        Document html = Jsoup.parse(indexFile, null)
        def executedTestClasses = html.select("div:has(h2:contains(Classes)).tab a").collect { it.text() }
        executedTestClasses
    }

    private File getIndexFile() {
        new File(htmlReportDirectory, "index.html")
    }

    private void assertHtmlReportForTestClassExists(String... classNames) {
        classNames.each {
            assert new File(htmlReportDirectory, "classes/${FileUtils.toSafeFileName(it)}.html").file
        }
    }

    boolean testClassExists(String testClass) {
        return new File(htmlReportDirectory, "classes/${FileUtils.toSafeFileName(testClass)}.html").exists()
    }

    boolean testClassDoesNotExist(String testClass) {
        return !testClassExists(testClass)
    }

    TestClassExecutionResult testClass(String testClass) {
        return new HtmlTestClassExecutionResult(new File(htmlReportDirectory, "classes/${FileUtils.toSafeFileName(testClass)}.html"))
    }

    TestClassExecutionResult testClassStartsWith(String testClass) {
        return new HtmlTestClassExecutionResult(new File(htmlReportDirectory, "classes").listFiles().find { it.name.startsWith(FileUtils.toSafeFileName(testClass)) })
    }

    @Override
    int getTotalNumberOfTestClassesExecuted() {
        return getExecutedTestClasses().size()
    }

    private static class HtmlTestClassExecutionResult implements TestClassExecutionResult {
        private String classDisplayName
        private File htmlFile
        private List<TestCase> testsExecuted = []
        private List<TestCase> testsSucceeded = []
        private List<TestCase> testsFailures = []
        private Set<TestCase> testsSkipped = []
        private Document html

        public HtmlTestClassExecutionResult(File htmlFile) {
            this.htmlFile = htmlFile;
            this.html = Jsoup.parse(htmlFile, null)
            parseTestClassFile()
        }

        private extractTestCaseTo(String cssSelector, Collection<TestCase> target) {
            html.select(cssSelector).each {
                def testDisplayName = it.textNodes().first().wholeText.trim()
                def testName = hasMethodNameColumn() ? it.nextElementSibling().text() : testDisplayName
                def failureMessage = getFailureMessages(testName)
                def testCase = testCase(testName, testDisplayName, failureMessage)
                testsExecuted << testCase
                target << testCase
            }
        }

        private boolean hasMethodNameColumn() {
            return html.select('tr > th').size() == 4
        }

        private void parseTestClassFile() {
            // " > TestClass" -> "TestClass"
            classDisplayName = html.select('div.breadcrumbs').first().textNodes().last().wholeText.trim().substring(3)
            extractTestCaseTo("tr > td.success:eq(0)", testsSucceeded)
            extractTestCaseTo("tr > td.failures:eq(0)", testsFailures)
            extractTestCaseTo("tr > td.skipped:eq(0)", testsSkipped)
        }

        List<String> getFailureMessages(String testmethod) {
            html.select("div.test:has(a[name='$testmethod']) > span > pre").collect { it.text() }
        }

        TestClassExecutionResult assertTestsExecuted(String... testNames) {
            return assertTestsExecuted(testNames.collect { testCase(it) } as TestCase[])
        }

        @Override
        TestClassExecutionResult assertTestsExecuted(TestCase... testCases) {
            def executedAndNotSkipped = testsExecuted - testsSkipped
            assert executedAndNotSkipped.containsAll(testCases)
            assert executedAndNotSkipped.size() == testCases.size()
            return this
        }

        TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
            assert tests == testsExecuted.size()
            assert failures == testsFailures.size()
            return this
        }

        int getTestCount() {
            return testsExecuted.size()
        }

        TestClassExecutionResult assertTestsSkipped(String... testNames) {
            assert testsSkipped == testNames.collect { testCase(it) } as Set
            return this
        }

        @Override
        TestClassExecutionResult assertTestPassed(String name, String displayName) {
            assert testsSucceeded.contains(testCase(name, displayName))
            return this
        }

        int getTestSkippedCount() {
            return testsSkipped.size()
        }

        TestClassExecutionResult assertTestPassed(String name) {
            assert testsSucceeded.contains(testCase(name))
            return this
        }

        @Override
        TestClassExecutionResult assertTestFailed(String name, String displayName, Matcher<? super String>... messageMatchers) {
            assert testFailed(name, displayName, messageMatchers)
            return this
        }

        @Override
        TestClassExecutionResult assertTestFailedIgnoreMessages(String name) {
            def fullMessages = collectTestFailFullMessages(name, name)
            assert !fullMessages.isEmpty()
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
            def fullMessages = collectTestFailFullMessages(name, displayName)
            if (fullMessages.isEmpty()) {
                return false
            }
            def messages = fullMessages.collect { it.readLines().first() }
            if (messages.size() != messageMatchers.length) {
                return false
            }
            for (int i = 0; i < messageMatchers.length; i++) {
                if (!messageMatchers[i].matches(messages[i]) && !messageMatchers[i].matches(fullMessages[i])) {
                    return false
                }
            }
            return true
        }

        private List<String> collectTestFailFullMessages(String name, String displayName) {
            def testCase = testsFailures.grep { it.name == name && it.displayName == displayName }
            if (testCase.isEmpty()) {
                return Collections.emptyList()
            }

            return testCase.first().messages
        }

        @Override
        TestClassExecutionResult assertTestSkipped(String name, String displayName) {
            assert testsSkipped.contains(testCase(name, displayName))
            return this
        }

        TestClassExecutionResult assertExecutionFailedWithCause(Matcher<? super String> causeMatcher) {
            String failureMethodName = EXECUTION_FAILURE
            def testCase = testsFailures.find { it.name == failureMethodName }
            assert testCase

            String causeLinePrefix = "Caused by: "
            def cause = testCase.messages.first().readLines().find { it.startsWith causeLinePrefix }?.substring(causeLinePrefix.length())

            assertThat(cause, causeMatcher)
            this
        }

        @Override
        TestClassExecutionResult assertDisplayName(String classDisplayName) {
            assert classDisplayName == classDisplayName
            this
        }

        TestClassExecutionResult assertTestSkipped(String name) {
            assert testsSkipped.contains(testCase(name))
            return this
        }

        TestClassExecutionResult assertConfigMethodPassed(String name) {
            return null
        }

        TestClassExecutionResult assertConfigMethodFailed(String name) {
            return null
        }

        TestClassExecutionResult assertStdout(Matcher<? super String> matcher) {
            return assertOutput('Standard output', matcher)
        }

        TestClassExecutionResult assertTestCaseStdout(String testCaseName, Matcher<? super String> matcher) {
            throw new UnsupportedOperationException()
        }

        TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
            return assertOutput('Standard error', matcher)
        }

        private HtmlTestClassExecutionResult assertOutput(heading, Matcher<? super String> matcher) {
            def tabs = html.select("div.tab")
            def tab = tabs.find { it.select("h2").text() == heading }
            assert matcher.matches(tab ? TextUtil.normaliseLineSeparators(tab.select("span > pre").first().textNodes().first().wholeText) : "")
            return this
        }

        TestClassExecutionResult assertTestCaseStderr(String testCaseName, Matcher<? super String> matcher) {
            throw new UnsupportedOperationException()
        }
    }
}
