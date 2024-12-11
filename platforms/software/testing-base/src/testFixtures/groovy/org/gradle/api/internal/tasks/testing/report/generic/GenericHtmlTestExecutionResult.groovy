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
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.Path
import org.gradle.util.internal.TextUtil
import org.hamcrest.Matcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import java.nio.file.Files
import java.util.stream.Collectors
import java.util.stream.Stream

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
        }.flatten() as Set<Path>
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
    TestPathExecutionResult testPath(String testPath) {
        return new HtmlTestPathExecutionResult(diskPathForTestPath(testPath).toFile())
    }

    @Override
    boolean testPathExists(String testPath) {
        return Files.exists(diskPathForTestPath(testPath))
    }

    @Override
    GenericTestExecutionResult assertMetadata(List<String> keys) {
        def metadataElems = Jsoup.parse(htmlReportDirectory.toPath().resolve("index.html").toFile(), null).select('.key')
        assertThat(metadataElems.collect() { it.text() }, equalTo(keys))
        return this
    }

    private static class HtmlTestPathExecutionResult implements TestPathExecutionResult {
        private String pathDisplayName
        private File htmlFile
        private List<String> testsExecuted = []
        private List<String> testsSucceeded = []
        private List<String> testsFailures = []
        private List<String> testsSkipped = []
        private Document html

        HtmlTestPathExecutionResult(File htmlFile) {
            this.htmlFile = htmlFile;
            this.html = Jsoup.parse(htmlFile, null)
            parseFile()
        }

        private extractTestCaseTo(String cssSelector, Collection<String> target) {
            html.select(cssSelector).each {
                def testDisplayName = it.textNodes().first().wholeText.trim()
                def testName = hasNameColumn() ? it.nextElementSibling().text() : testDisplayName
                testsExecuted << testName
                target << testName
            }
        }

        private boolean hasNameColumn() {
            return html.select('tr > th').size() == 7
        }

        private void parseFile() {
            // " > TestClass" -> "TestClass"
            pathDisplayName = html.select('div.breadcrumbs').first().textNodes().last().wholeText.trim().substring(3)
            extractTestCaseTo("tr > td.success:eq(0)", testsSucceeded)
            extractTestCaseTo("tr > td.failures:eq(0)", testsFailures)
            extractTestCaseTo("tr > td.skipped:eq(0)", testsSkipped)
        }

        @Override
        TestPathExecutionResult assertChildrenExecuted(String... testNames) {
            def executedAndNotSkipped = testsExecuted - testsSkipped
            assertThat(executedAndNotSkipped, equalTo(testNames as Set))
            return this
        }

        @Override
        TestPathExecutionResult assertChildCount(int tests, int failures, int errors) {
            assert tests == testsExecuted.size()
            assert failures == testsFailures.size()
            return this
        }

        @Override
        TestPathExecutionResult assertStdout(Matcher<? super String> matcher) {
            return assertOutput('standard output', matcher)
        }

        @Override
        TestPathExecutionResult assertStderr(Matcher<? super String> matcher) {
            return assertOutput('error output', matcher)
        }

        private TestPathExecutionResult assertOutput(heading, Matcher<? super String> matcher) {
            def tabs = html.select("div.tab")
            def tab = tabs.find { it.select("h2").text() == heading }
            assert matcher.matches(tab ? TextUtil.normaliseLineSeparators(tab.select("span > pre").first().textNodes().first().wholeText) : "")
            return this
        }

        @Override
        TestPathExecutionResult assertHasResult(TestResult.ResultType expectedResultType) {
            assert getResultType() == expectedResultType
            return this
        }

        private getResultType() {
            // Currently the report only contains leaf results
            assertChildCount(0, 0, 0)
            def successRateElement = html.selectFirst('.summary .successRate')
            if (successRateElement.hasClass("failures")) {
                return TestResult.ResultType.FAILURE
            } else if (successRateElement.hasClass("skipped")) {
                return TestResult.ResultType.SKIPPED
            } else {
                return TestResult.ResultType.SUCCESS
            }
        }

        @Override
        TestPathExecutionResult assertFailureMessages(Matcher<? super String> matcher) {
            def detailsElem = html.selectFirst('.result-details pre')
            assertThat(detailsElem == null ? '' : detailsElem.text(), matcher)
            return this
        }

        @Override
        TestPathExecutionResult assertMetadata(List<String> keys) {
            def metadataElems = html.select('.key')
            assertThat(metadataElems.collect() { it.text() }, equalTo(keys))
            return this
        }
    }
}
