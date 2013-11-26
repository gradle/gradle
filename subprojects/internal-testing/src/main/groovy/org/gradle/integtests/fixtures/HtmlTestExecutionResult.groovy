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
import org.hamcrest.Matcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class HtmlTestExecutionResult implements TestExecutionResult {

    private File htmlReportDirectory

    public HtmlTestExecutionResult(File projectDirectory, String testReportDirectory = "build/reports/tests") {
        this.htmlReportDirectory = new File(projectDirectory, testReportDirectory);
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        indexContainsTestClass(testClasses)
        assertHtmlReportForTestClassExists(testClasses)
        return this
    }

    private void indexContainsTestClass(String... expectedTestClasses) {
        def indexFile = new File(htmlReportDirectory, "index.html")
        assert indexFile.exists()
        Document html = Jsoup.parse(indexFile, null)
        def executedTestClasses = html.select("div:has(h2:contains(Classes)).tab a").collect { it.text() }
        assert executedTestClasses.containsAll(expectedTestClasses)
    }

    private void assertHtmlReportForTestClassExists(String... classNames) {
        classNames.each {
            assert new File(htmlReportDirectory, "classes/${FileUtils.toSafeFileName(it)}.html").file
        }
    }

    TestClassExecutionResult testClass(String testClass) {
        return new HtmlTestClassExecutionResult(new File(htmlReportDirectory, "classes/${FileUtils.toSafeFileName(testClass)}.html"));
    }

    private static class HtmlTestClassExecutionResult implements TestClassExecutionResult {
        private File htmlFile
        private List<String> testsExecuted = []
        private List<String> testsSucceeded = []
        private Map<String, List<String>> testsFailures = [:]
        private Set<String> testsSkipped = []
        private Document html

        public HtmlTestClassExecutionResult(File htmlFile) {
            this.htmlFile = htmlFile;
            this.html = Jsoup.parse(htmlFile, null)
            parseTestClassFile()
        }

        private void parseTestClassFile() {
            html.select("tr > td.success:eq(0)").each {
                def testName = it.textNodes().first().wholeText.trim()
                testsExecuted << testName
                testsSucceeded << testName

            }
            html.select("tr > td.failures:eq(0)").each {
                def testName = it.textNodes().first().wholeText.trim()
                testsExecuted << testName
                def failures = getFailureMessages(testName);
                testsFailures[it.text()] = failures
            }

            html.select("tr > td.skipped:eq(0)").each {
                def testName = it.textNodes().first().wholeText.trim()
                testsSkipped << testName
                testsExecuted << testName
            }
        }

        List<String> getFailureMessages(String testmethod) {
            html.select("div.test:has(a[name=$testmethod]) > span > pre").collect { it.text().readLines().first() }
        }

        TestClassExecutionResult assertTestsExecuted(String... testNames) {
            assert testsExecuted - testsSkipped == testNames as List
            return this
        }

        TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
            assert tests == testsExecuted.size()
            assert failures == testsFailures.size()
            return this
        }

        TestClassExecutionResult assertTestsSkipped(String... testNames) {
            assert testsSkipped == testNames as Set
            return this
        }

        TestClassExecutionResult assertTestPassed(String name) {
            assert testsSucceeded.contains(name);
            return this
        }

        TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
            assert testsFailures.containsKey(name)
            def messages = testsFailures[name]
            assert messages.size() == messageMatchers.length
            for (int i = 0; i < messageMatchers.length; i++) {
                assert messageMatchers[i].matches(messages[i])
            }
            return this
        }

        TestClassExecutionResult assertTestSkipped(String name) {
            assert testsSkipped.contains(name);
            return this
        }

        TestClassExecutionResult assertConfigMethodPassed(String name) {
            return null
        }

        TestClassExecutionResult assertConfigMethodFailed(String name) {
            return null
        }

        TestClassExecutionResult assertStdout(Matcher<? super String> matcher) {
            def tabs = html.select("div.tab")
            def tab = tabs.find { it.select("h2").text() == 'Standard output' }
            assert matcher.matches(tab ? tab.select("span > pre").first().textNodes().first().wholeText : "")
            return this;
        }

        TestClassExecutionResult assertTestCaseStdout(String testCaseName, Matcher<? super String> matcher) {
            throw new UnsupportedOperationException()
        }

        TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
            def tabs = html.select("div.tab")
            def tab = tabs.find { it.select("h2").text() == 'Standard error' }
            assert matcher.matches(tab ? tab.select("span > pre").first().textNodes().first().wholeText : "")
            return this;
        }

        TestClassExecutionResult assertTestCaseStderr(String testCaseName, Matcher<? super String> matcher) {
            throw new UnsupportedOperationException()
        }

    }
}
