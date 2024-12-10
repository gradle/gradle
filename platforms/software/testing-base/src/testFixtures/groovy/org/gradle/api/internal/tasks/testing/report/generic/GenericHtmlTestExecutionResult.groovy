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

import com.google.common.collect.Iterables
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.util.Path
import org.gradle.util.internal.TextUtil
import org.hamcrest.Matcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import java.nio.file.Files
import java.util.stream.Collectors
import java.util.stream.Stream

import static org.gradle.integtests.fixtures.DefaultTestExecutionResult.testCase
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

class GenericHtmlTestExecutionResult implements GenericTestExecutionResult {

    private File htmlReportDirectory

    GenericHtmlTestExecutionResult(File projectDirectory, String testReportDirectory = "build/reports/tests/test") {
        this.htmlReportDirectory = new File(projectDirectory, testReportDirectory);
    }

    @Override
    GenericTestExecutionResult assertTestPathsExecuted(String... testPaths) {
        def executedTestPaths = getExecutedTestPaths()
        // We always will detect ancestors of the executed test paths as well, so add them to the set
        Set<Path> extendedTestPaths = testPaths.collect {
            Path.path(it)
        }.collect {
            Iterables.concat([it], it.ancestors())
        }.flatten() as Set
        assertThat(executedTestPaths, equalTo(extendedTestPaths))
        return this
    }

    @Override
    GenericTestExecutionResult assertTestPathsNotExecuted(String... testPaths) {
        def executedTestPaths = getExecutedTestPaths()
        assertThat(executedTestPaths, not(hasItems(testPaths.collect { Path.path(it) }.toArray(Path[]::new))))
        return this
    }

    private Set<Path> getExecutedTestPaths() {
        def reportPath = htmlReportDirectory.toPath()
        try (Stream<java.nio.file.Path> paths = Files.walk(reportPath)) {
            return paths.filter {
                it.getFileName().toString() == "index.html"
            }.map {
                def relative = reportPath.relativize(it)
                def testPath = Path.ROOT
                // We use -1 to exclude the index.html segment
                for (int i = 0; i < relative.getNameCount() - 1; i++) {
                    testPath = testPath.child(relative.getName(i).toString())
                }
                testPath
            }.collect(Collectors.toSet())
        }
    }

    private java.nio.file.Path diskPathForTestPath(String testPath) {
        htmlReportDirectory.toPath().resolve(GenericHtmlTestReport.getFilePath(Path.path(testPath)))
    }

    @Override
    TestClassExecutionResult testPath(String testPath) {
        return new HtmlTestClassExecutionResult(diskPathForTestPath(testPath).toFile())
    }

    @Override
    boolean testPathExists(String testPath) {
        return Files.exists(diskPathForTestPath(testPath))
    }

    private static class HtmlTestClassExecutionResult implements TestClassExecutionResult {
        private String classDisplayName
        private File htmlFile
        private List<TestCase> testsExecuted = []
        private List<TestCase> testsSucceeded = []
        private List<TestCase> testsFailures = []
        private Set<TestCase> testsSkipped = []
        private Document html

        HtmlTestClassExecutionResult(File htmlFile) {
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
