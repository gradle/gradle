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

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMultiset
import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.google.common.collect.Multisets
import com.google.common.collect.Sets
import com.google.common.collect.Streams
import org.gradle.api.Action
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.lazy.Lazy
import org.gradle.util.Path
import org.gradle.util.internal.TextUtil
import org.hamcrest.Matcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

import java.nio.file.Files
import java.util.stream.Collectors
import java.util.stream.Stream

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.greaterThanOrEqualTo
import static org.hamcrest.Matchers.notNullValue
import static org.junit.jupiter.api.Assertions.fail

class GenericHtmlTestExecutionResult implements GenericTestExecutionResult {
    private final Lazy<Set<Path>> executedTestPathsLazy = Lazy.locking().of({
        def reportPath = htmlReportDirectory.toPath()
        try (Stream<java.nio.file.Path> paths = Files.walk(reportPath)) {
            return paths.filter {
                it.getFileName().toString().endsWith(".html")
            }.collect {
                def html = Jsoup.parse(it.toFile(), null)
                def breadcrumbs = html.selectFirst(".breadcrumbs")
                if (breadcrumbs == null) {
                    return Path.ROOT
                }
                def elements = breadcrumbs.select(".breadcrumb").collect { it.text().trim() }
                if (elements.size() == 1 || elements[0] != "all") {
                    throw new IllegalStateException("First element should always be 'all'")
                }
                def path = Path.ROOT
                for (int i = 1; i < elements.size(); i++) {
                    path = path.child(elements[i])
                }
                def childrenWithoutFiles = html.select("td.path").collect { path.child(it.text()) }
                return [path] + childrenWithoutFiles
            }.flatten().toSet()
        }
    })
    private final File htmlReportDirectory
    private final TestFramework testFramework

    GenericHtmlTestExecutionResult(File projectDirectory, String testReportDirectory = "build/reports/tests/test", TestFramework testFramework) {
        this.htmlReportDirectory = new File(projectDirectory, testReportDirectory)
        this.testFramework = testFramework
        // For debugging purposes, always log the location of the report
        println "HTML test report directory: ${htmlReportDirectory}"
    }

    /**
     * Only public for HtmlTestExecutionResult to use.
     * Prefer to add additional methods for the exact assertion over using this method with arbitrary checking.
     *
     * @return the set of executed test paths
     */
    Set<Path> getExecutedTestPaths() {
        return executedTestPathsLazy.get()
    }

    GenericTestExecutionResult assertHtml(String cssQuery, Action<Collection<?>> action) {
        def parsedHtml = Jsoup.parse(htmlReportDirectory.toPath().resolve("index.html").toFile(), null)
        def matched = parsedHtml.select(cssQuery)
        assert matched : "Queried HTML report for $cssQuery"
        action.execute(matched)
        return this
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    @Override
    GenericTestExecutionResult assertTestPathsExecuted(String... testPaths) {
        // We always will detect ancestors of the executed test paths as well, so add them to the set
        Set<Path> extendedTestPaths = Stream.of(testPaths)
            .map { frameworkTestPath(it) }
            .map { Path.path(it) }
            .flatMap {
                Stream.concat(
                    Stream.of(it),
                    Streams.stream(it.ancestors()),
                )
            }
            .collect(Collectors.toSet())
        def missingPaths = Sets.difference(extendedTestPaths, executedTestPaths)
        def unexpectedPaths = Sets.difference(executedTestPaths, extendedTestPaths)
        if (!missingPaths.isEmpty() && !unexpectedPaths.isEmpty()) {
            fail("""Expected paths (${extendedTestPaths.size()}) do not match actual executed paths (${executedTestPaths.size()}).
Missing paths: ${missingPaths}
Unexpected paths: ${unexpectedPaths}""")
        } else if (!missingPaths.isEmpty()) {
            fail("""Expected paths (${extendedTestPaths.size()}) do not match actual executed paths (${executedTestPaths.size()}).
Missing paths: ${missingPaths}""")
        } else if (!unexpectedPaths.isEmpty()) {
            fail("""Expected paths (${extendedTestPaths.size()}) do not match actual executed paths (${executedTestPaths.size()}).
Unexpected paths: ${unexpectedPaths}""")
        }
        return this
    }

    // Differs from `assertTestPathsExecuted` in that it only checks that at least the given paths were executed, not that they were the only ones.
    @Override
    GenericTestExecutionResult assertAtLeastTestPathsExecuted(String... testPaths) {
        def frameworkTestPaths = testPaths.collect { frameworkTestPath(it) }
        return assertAtLeastTestPathsExecutedPreNormalized(frameworkTestPaths.toArray([] as String[]))
    }

    /**
     * Like {@link #assertAtLeastTestPathsExecuted(String...)}, but the paths must be in the test framework specific format already.
     */
    GenericTestExecutionResult assertAtLeastTestPathsExecutedPreNormalized(String... testPaths) {
        // We always will detect ancestors of the executed test paths as well, so add them to the set
        Path[] extendedTestPaths = Stream.of(testPaths)
            .map {it == "" ? ":" : it }
            .map { Path.path(it) }
            .flatMap {
                Stream.concat(
                    Stream.of(it),
                    Streams.stream(it.ancestors()),
                )
            }
            .distinct()
            .toArray(Path[]::new)
        assertThat("at least the expected paths must exist", executedTestPaths, hasItems(extendedTestPaths))
        return this
    }

    @Override
    GenericTestExecutionResult assertTestPathsNotExecuted(String... testPaths) {
        assertThat(executedTestPaths, not(hasItems(testPaths.collect {frameworkTestPath(it) }.collect { Path.path(it) }.toArray(Path[]::new))))
        return this
    }

    @Override
    TestPathExecutionResult testPath(String rootTestPath) {
        assertAtLeastTestPathsExecuted(rootTestPath)

        def reportPath = diskPathForTestPath(frameworkTestPath(rootTestPath))
        if (Files.exists(reportPath)) {
            return new HtmlTestPathExecutionResult(testFramework, reportPath.toFile())
        } else {
            return new TestPathExecutionResult() {
                private final TestPathRootExecutionResult doesNotExist = new TestPathRootExecutionResult() {
                    @Override
                    TestPathRootExecutionResult assertOnlyChildrenExecuted(String... testNames) {
                        assert !testNames.empty
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertChildrenExecuted(String... testNames) {
                        assert !testNames.empty
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertChildCount(int tests, int failures) {
                        assert tests == 0
                        return this
                    }

                    @Override
                    int getExecutedChildCount() {
                        return 0
                    }

                    @Override
                    TestPathRootExecutionResult assertChildrenSkipped(String... testNames) {
                        assert !testNames.empty
                        return this
                    }

                    @Override
                    int getSkippedChildCount() {
                        return 0
                    }

                    @Override
                    TestPathRootExecutionResult assertChildrenFailed(String... testNames) {
                        assert !testNames.empty
                        return this
                    }

                    @Override
                    int getFailedChildCount() {
                        return 0
                    }

                    @Override
                    TestPathRootExecutionResult assertStdout(Matcher<? super String> matcher) {
                        matcher.matches("")
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertStderr(Matcher<? super String> matcher) {
                        matcher.matches("")
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertHasResult(TestResult.ResultType resultType) {
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertDisplayName(Matcher<? super String> matcher) {
                        assert false
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertFailureMessages(Matcher<? super String> matcher) {
                        assert false
                        return this
                    }

                    @Override
                    String getFailureMessages() {
                        assert false
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertMetadataKeys(List<String> keys) {
                        assert false
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertMetadata(List<Map.Entry<String, String>> metadata) {
                        assert false
                        return this
                    }

                    @Override
                    TestPathRootExecutionResult assertFileAttachments(Map<String, TestPathRootExecutionResult.ShowAs> expectedAttachments) {
                        assert false
                        return this
                    }
                }

                @Override
                TestPathRootExecutionResult onlyRoot() {
                    return doesNotExist
                }

                @Override
                TestPathRootExecutionResult singleRootWithRun(int runNumber) {
                    assert false
                    return doesNotExist
                }

                @Override
                TestPathRootExecutionResult root(String rootName) {
                    assert false
                    return doesNotExist
                }

                @Override
                TestPathRootExecutionResult rootAndRun(String rootName, int runNumber) {
                    assert false
                    return doesNotExist
                }

                @Override
                List<String> getRootNames() {
                    return [rootTestPath]
                }

                @Override
                int getRunCount(String rootName) {
                    assert false
                    return 0
                }
            }
        }
    }

    TestPathExecutionResult testPathPreNormalized(String rootTestPath) {
        assertAtLeastTestPathsExecutedPreNormalized(rootTestPath)
        return new HtmlTestPathExecutionResult(testFramework, diskPathForTestPath(rootTestPath).toFile())
    }

    @Override
    TestPathExecutionResult testPath(String... testPathElements) {
        String joined = Stream.of(testPathElements).collect(Collectors.joining(":", ":", ""))
        return testPath(joined)
    }

    @Override
    boolean testPathExists(String testPath) {
        String frameworkPathToTest = frameworkTestPath(testPath)
        return Files.exists(diskPathForTestPath(frameworkPathToTest))
    }

    @Override
    GenericTestExecutionResult assertMetadata(List<String> keys) {
        def metadataElems = Jsoup.parse(htmlReportDirectory.toPath().resolve("index.html").toFile(), null).select('.key')
        assertThat(metadataElems.collect() { it.text() }, equalTo(keys))
        return this
    }

    @VisibleForTesting
    String frameworkTestPath(String testPath) {
        if (testFramework == TestFramework.CUSTOM) {
            return testPath
        }

        String basePrefix, baseSuffix
        if (Strings.isNullOrEmpty(testPath)) {
            basePrefix = ""
            baseSuffix = ""
        } else if (testPath == ":") {
            basePrefix = ""
            baseSuffix = ""
        } else {
            int lastColon = testPath.lastIndexOf(':')
            if (lastColon == -1) {
                basePrefix = testPath
                baseSuffix = ""
            } else if (lastColon == 0) { // path is :ClassName
                basePrefix = testPath.substring(1)
                baseSuffix = ""
            } else {
                if (testPath.startsWith(":")) {
                    basePrefix = testPath.substring(1, lastColon)
                } else {
                    basePrefix = testPath.substring(0, lastColon)
                }
                baseSuffix = testPath.substring(lastColon + 1)
            }
        }

        def prefix = Strings.isNullOrEmpty(basePrefix) ? ":" : ":" + basePrefix
        def suffix = Strings.isNullOrEmpty(baseSuffix) ? "" : ":" + testFramework.getTestCaseName(baseSuffix)
        return prefix + suffix
    }

    private java.nio.file.Path diskPathForTestPath(String frameworkTestPath) {
        if (Strings.isNullOrEmpty(frameworkTestPath)) {
            return htmlReportDirectory.toPath().resolve("index.html")
        }
        java.nio.file.Path nonLeafPath = htmlReportDirectory.toPath().resolve(
            GenericHtmlTestReportGenerator.getFilePath(Path.path(frameworkTestPath), false)
        )
        if (Files.exists(nonLeafPath)) {
            return nonLeafPath
        } else {
            return htmlReportDirectory.toPath().resolve(
                GenericHtmlTestReportGenerator.getFilePath(Path.path(frameworkTestPath), true)
            )
        }
    }

    private static Map<String, Element> getTabs(Element base) {
        def container = base.selectFirst('.tab-container')
        // Check container only for presence. If no container, return empty map.
        // Otherwise, we expect the structure to be correct.
        if (container == null) {
            return Collections.emptyMap()
        }
        def tabs = container.select('> .tab')
        def tabNames = container.select('> .tabLinks > li')
        assert tabs.size() == tabNames.size()
        Map<String, Element> result = new LinkedHashMap<>()
        for (int i = 0; i < tabs.size(); i++) {
            def tabName = tabNames.get(i).text()
            assert !result.containsKey(tabName) : "Duplicate tab name: " + tabName
            result[tabName] = tabs.get(i)
        }
        return result
    }

    private static class HtmlTestPathExecutionResult implements TestPathExecutionResult {
        private final TestFramework testFramework
        private final List<String> rootNames = []
        private final List<String> rootDisplayNames = []
        private final ListMultimap<String, Element> rootAndRunElements = LinkedListMultimap.create()

        HtmlTestPathExecutionResult(TestFramework testFramework, File htmlFile) {
            this.testFramework = testFramework
            Document html = Jsoup.parse(htmlFile, null)
            Map<String, Element> rootElements = getTabs(html)
            rootElements.forEach { name, content ->
                rootNames.add(name)
                rootDisplayNames.add(content.selectFirst('h1').text())
                Map<String, Element> subTabs = getTabs(content)
                if (subTabs.containsKey("summary")) {
                    // Only a single run, these tabs are the run details tabs
                    rootAndRunElements.put(name, content)
                } else {
                    // Multiple runs
                    rootAndRunElements.putAll(name, subTabs.values())
                }
            }
        }

        private getSingleRoot() {
            assertThat(
                "has multiple roots: " + rootAndRunElements.keySet(),
                rootAndRunElements.keySet().size(),
                equalTo(1)
            )
            Multimaps.asMap(rootAndRunElements).values().first()
        }

        @Override
        TestPathRootExecutionResult onlyRoot() {
            List<Element> singleRoot = getSingleRoot()
            assertThat(
                "has multiple runs",
                singleRoot.size(),
                equalTo(1)
            )
            return new HtmlTestPathRootExecutionResult(testFramework, singleRoot.first(), rootDisplayNames.first())
        }

        @Override
        TestPathRootExecutionResult singleRootWithRun(int runNumber) {
            List<Element> singleRoot = getSingleRoot()
            return new HtmlTestPathRootExecutionResult(testFramework, singleRoot.get(runNumber - 1), rootDisplayNames.first())
        }

        @Override
        TestPathRootExecutionResult root(String rootName) {
            assertThat(rootAndRunElements.keySet(), hasItems(rootName))
            List<Element> runs = rootAndRunElements.get(rootName)
            assertThat(
                "root '" + rootName + "' has multiple runs",
                runs.size(),
                equalTo(1)
            )
            def index = rootNames.indexOf(rootName)
            return new HtmlTestPathRootExecutionResult(testFramework, runs.first(), rootDisplayNames[index])
        }

        @Override
        TestPathRootExecutionResult rootAndRun(String rootName, int runNumber) {
            assertThat(rootAndRunElements.keySet(), hasItems(rootName))
            List<Element> runs = rootAndRunElements.get(rootName)
            assertThat(
                "root '" + rootName + "' has not enough runs (requested run number: "
                    + runNumber + ", but only has " + runs.size() + " runs)",
                runs.size(),
                greaterThanOrEqualTo(runNumber)
            )
            def index = rootNames.indexOf(rootName)
            return new HtmlTestPathRootExecutionResult(testFramework, runs.get(runNumber - 1), rootDisplayNames[index])
        }

        @Override
        List<String> getRootNames() {
            return rootNames
        }

        @Override
        int getRunCount(String rootName) {
            return rootAndRunElements.get(rootName).size()
        }
    }

    private static class HtmlTestPathRootExecutionResult implements TestPathRootExecutionResult {
        private final TestFramework testFramework
        private final Element html
        private final String displayName
        private Multimap<String, TestInfo> testsExecuted = LinkedListMultimap.create()
        private Multimap<String, TestInfo> testsSucceeded = LinkedListMultimap.create()
        private Multimap<String, TestInfo> testsFailures = LinkedListMultimap.create()
        private Multimap<String, TestInfo> testsSkipped = LinkedListMultimap.create()

        HtmlTestPathRootExecutionResult(TestFramework testFramework, Element html, String displayName) {
            this.testFramework = testFramework
            this.html = html
            this.displayName = displayName
            extractCases()
        }

        private void extractCases() {
            def summarySection = getTabs(html).get("summary")
            assertThat("no summary section found", summarySection, notNullValue())
            def allSection = getTabs(summarySection).get("All")
            def allTestsContainer = allSection == null ? summarySection : allSection
            extractTestCaseTo(allTestsContainer, "tr > td.success:eq(0)", testsSucceeded)
            extractTestCaseTo(allTestsContainer, "tr > td.failures:eq(0)", testsFailures)
            extractTestCaseTo(allTestsContainer, "tr > td.skipped:eq(0)", testsSkipped)
        }

        private extractTestCaseTo(Element element, String cssSelector, Multimap<String, TestInfo> target) {
            def hasNameColumn = hasNameColumn(element)
            element.select(cssSelector).each {
                def testDisplayName = it.text().trim()
                def testName = hasNameColumn ? it.nextElementSibling().text().trim() : testDisplayName
                def testInfo = new TestInfo(testName, testDisplayName)
                testsExecuted.put(testName, testInfo)
                target.put(testName, testInfo)
            }
        }

        private static boolean hasNameColumn(Element element) {
            return element.select('tr > th').size() == 7
        }

        private ImmutableMultiset<String> frameworkTestNames(String... testNames) {
            return Stream.of(testNames)
                .map { testFramework.getTestCaseName(it) }
                .collect(ImmutableMultiset.toImmutableMultiset())
        }

        @Override
        TestPathRootExecutionResult assertOnlyChildrenExecuted(String... testNames) {
            def executedAndNotSkipped = Multisets.difference(testsExecuted.keys(), testsSkipped.keys())
            assertThat("in " + displayName, executedAndNotSkipped, equalTo(frameworkTestNames(testNames)))
            return this
        }

        @Override
        TestPathRootExecutionResult assertChildrenExecuted(String... testNames) {
            def executedAndNotSkipped = Multisets.difference(testsExecuted.keys(), testsSkipped.keys())
            assertThat("in " + displayName, executedAndNotSkipped, hasItems(frameworkTestNames(testNames).toArray(String[]::new)))
            return this
        }

        @Override
        TestPathRootExecutionResult assertChildCount(int tests, int failures) {
            assertThat("in " + displayName, testsExecuted.size(), equalTo(tests))
            assertThat("in " + displayName, testsFailures.size(), equalTo(failures))
            return this
        }

        @Override
        int getExecutedChildCount() {
            return testsExecuted.size()
        }

        @Override
        TestPathRootExecutionResult assertChildrenSkipped(String... testNames) {
            assertThat("in " + displayName, testsSkipped.keys(), equalTo(frameworkTestNames(testNames)))
            return this
        }

        @Override
        int getSkippedChildCount() {
            return testsSkipped.size()
        }

        @Override
        TestPathRootExecutionResult assertChildrenFailed(String... testNames) {
            assertThat("in " + displayName, testsFailures.keys(), equalTo(frameworkTestNames(testNames)))
            return this
        }

        @Override
        int getFailedChildCount() {
            return testsFailures.size()
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
            assertThat(
                "in " + displayName,
                tab ? TextUtil.normaliseLineSeparators(tab.select("span > pre").first().textNodes().first().wholeText)
                    : "",
                matcher,
            )
            return this
        }

        @Override
        TestPathRootExecutionResult assertHasResult(TestResult.ResultType expectedResultType) {
            assert getResultType() == expectedResultType
            return this
        }

        private getResultType() {
            // Currently the report only contains leaf results
            assertChildCount(0, 0)
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
        TestPathRootExecutionResult assertDisplayName(Matcher<? super String> matcher) {
            assertThat(displayName, matcher)
            return this
        }

        @Override
        TestPathRootExecutionResult assertFailureMessages(Matcher<? super String> matcher) {
            assertThat("in " + displayName, getFailureMessages(), matcher)
            return this
        }

        @Override
        String getFailureMessages() {
            html.selectFirst('.result-details pre')?.text() ?: ''
        }

        @Override
        TestPathRootExecutionResult assertMetadataKeys(List<String> expectedKeys) {
            def metadataKeys = html.select('.metadata td.key').collect() { it.text() }
            assertThat("in " + displayName, metadataKeys, equalTo(expectedKeys))
            return this
        }

        @Override
        TestPathRootExecutionResult assertMetadata(List<Map.Entry<String, String>> expectedMetadata) {
            def metadataKeys = html.select('.metadata td.key').collect() { it.text() }
            def metadataRenderedValues = html.select('.metadata td.value').collect { it.html()}
            def metadata = [metadataKeys, metadataRenderedValues].transpose().collect { List<String> it ->
                Maps.immutableEntry(it[0], it[1])
            }
            assertThat("in " + displayName, metadata, equalTo(expectedMetadata))
            return this
        }

        @Override
        TestPathRootExecutionResult assertFileAttachments(Map<String, ShowAs> expectedAttachments) {
            def fileAttachments = html.select('.attachments tr').findAll { it.getElementsByTag('td').size() > 0 }
            Map<String, ShowAs> actual = fileAttachments.collectEntries {
                def columns = it.getElementsByTag("td")
                assert columns.size() == 2 : "unexpected table"
                def key = columns[0]
                def content = columns[1]
                def shownAs
                if (content.getElementsByTag("img").size() > 0) {
                    shownAs = ShowAs.IMAGE
                } else if (content.getElementsByTag("video").size() > 0) {
                    shownAs = ShowAs.VIDEO
                } else if (content.getElementsByTag("a").size() > 0) {
                    shownAs = ShowAs.LINK
                } else {
                    shownAs = null
                }
                [key.text(), shownAs]
            }

            assertThat("in " + displayName, actual, equalTo(expectedAttachments))
            return this
        }
    }
}
