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

import com.google.common.collect.HashMultiset
import com.google.common.collect.ImmutableMultiset
import com.google.common.collect.Iterables
import com.google.common.collect.Multiset
import com.google.common.collect.Multisets
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.Path
import org.gradle.util.internal.TextUtil
import org.hamcrest.Matcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

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
        private final List<String> rootNames = []
        private final Map<String, Element> rootElements = [:]

        HtmlTestPathExecutionResult(File htmlFile) {
            Document html = Jsoup.parse(htmlFile, null)
            Element rootTabContainer = html.selectFirst('.tab-container')
            Elements tabs = rootTabContainer.select('> .tab')
            Elements tabNames = rootTabContainer.select('> .tabLinks > li')
            for (int i = 0; i < tabs.size(); i++) {
                def rootName = tabNames.get(i).text()
                rootNames.add(rootName)
                rootElements[rootName] = tabs.get(i)
            }
        }

        @Override
        TestPathRootExecutionResult onlyRoot() {
            assertThat("has multiple roots: " + rootElements.keySet(), rootElements.size(), equalTo(1))
            return new HtmlTestPathRootExecutionResult(rootElements.values().first())
        }

        @Override
        TestPathRootExecutionResult root(String rootName) {
            assertThat(rootElements.keySet(), hasItems(rootName))
            return new HtmlTestPathRootExecutionResult(rootElements[rootName])
        }

        @Override
        List<String> getRootNames() {
            return rootNames
        }
    }

    private static class HtmlTestPathRootExecutionResult implements TestPathRootExecutionResult {
        private Element html
        private Multiset<String> testsExecuted = HashMultiset.create()
        private Multiset<String> testsSucceeded = HashMultiset.create()
        private Multiset<String> testsFailures = HashMultiset.create()
        private Multiset<String> testsSkipped = HashMultiset.create()

        HtmlTestPathRootExecutionResult(Element html) {
            this.html = html
            extractCases()
        }

        private void extractCases() {
            extractTestCaseTo("tr > td.success:eq(0)", testsSucceeded)
            extractTestCaseTo("tr > td.failures:eq(0)", testsFailures)
            extractTestCaseTo("tr > td.skipped:eq(0)", testsSkipped)
        }

        private extractTestCaseTo(String cssSelector, Collection<String> target) {
            html.select(cssSelector).each {
                def testDisplayName = it.text().trim()
                def testName = hasNameColumn() ? it.nextElementSibling().text().trim() : testDisplayName
                testsExecuted << testName
                target << testName
            }
        }

        private boolean hasNameColumn() {
            return html.select('tr > th').size() == 7
        }

        @Override
        TestPathRootExecutionResult assertChildrenExecuted(String... testNames) {
            def executedAndNotSkipped = Multisets.difference(testsExecuted, testsSkipped)
            assertThat(executedAndNotSkipped, equalTo(ImmutableMultiset.copyOf(testNames)))
            return this
        }

        @Override
        TestPathRootExecutionResult assertChildCount(int tests, int failures, int errors) {
            assert tests == testsExecuted.size()
            assert failures == testsFailures.size()
            return this
        }

        @Override
        TestPathRootExecutionResult assertStdout(Matcher<? super String> matcher) {
            return assertOutput('standard output', matcher)
        }

        @Override
        TestPathRootExecutionResult assertStderr(Matcher<? super String> matcher) {
            return assertOutput('error output', matcher)
        }

        private TestPathRootExecutionResult assertOutput(heading, Matcher<? super String> matcher) {
            def tabs = html.select("div.tab")
            def tab = tabs.find { it.select("h2").text() == heading }
            assert matcher.matches(tab ? TextUtil.normaliseLineSeparators(tab.select("span > pre").first().textNodes().first().wholeText) : "")
            return this
        }

        @Override
        TestPathRootExecutionResult assertHasResult(TestResult.ResultType expectedResultType) {
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
        TestPathRootExecutionResult assertFailureMessages(Matcher<? super String> matcher) {
            def detailsElem = html.selectFirst('.result-details pre')
            assertThat(detailsElem == null ? '' : detailsElem.text(), matcher)
            return this
        }

        @Override
        TestPathRootExecutionResult assertMetadata(List<String> expectedKeys) {
            def metadataKeys = html.select('.metadata td.key').collect() { it.text() }
            assertThat(metadataKeys, equalTo(expectedKeys))
            return this
        }

        @Override
        TestPathRootExecutionResult assertMetadata(LinkedHashMap<String, String> expectedMetadata) {
            def metadataKeys = html.select('.metadata td.key').collect() { it.text() }
            def metadataRenderedValues = html.select('.metadata td.value').collect { it.html()}
            def metadata = [metadataKeys, metadataRenderedValues].transpose().collectEntries { key, value -> [key, value] }
            assertThat(metadata, equalTo(expectedMetadata))
            return this
        }
    }
}
