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
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.google.common.collect.Multisets
import com.google.common.collect.Sets
import com.google.common.collect.Streams
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
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.greaterThanOrEqualTo
import static org.junit.jupiter.api.Assertions.fail

class GenericHtmlTestExecutionResult implements GenericTestExecutionResult {
    private final Lazy<Set<Path>> executedTestPathsLazy = Lazy.locking().of({
        def reportPath = htmlReportDirectory.toPath()
        try (Stream<java.nio.file.Path> paths = Files.walk(reportPath)) {
            return paths.filter {
                it.getFileName().toString() == "index.html"
            }.map {
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
                return path
            }.collect(Collectors.toSet())
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
        return new HtmlTestPathExecutionResult(diskPathForTestPath(frameworkTestPath(rootTestPath)).toFile())
    }

    TestPathExecutionResult testPathPreNormalized(String rootTestPath) {
        assertAtLeastTestPathsExecutedPreNormalized(rootTestPath)
        return new HtmlTestPathExecutionResult(diskPathForTestPath(rootTestPath).toFile())
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

        return switch (testFramework) {
            case TestFramework.SPOCK, TestFramework.JUNIT4, TestFramework.SCALA_TEST,
                 TestFramework.XC_TEST, TestFramework.CUCUMBER, TestFramework.TEST_NG -> {
                def prefix = Strings.isNullOrEmpty(basePrefix) ? ":" : ":" + basePrefix
                def suffix = Strings.isNullOrEmpty(baseSuffix) ? "" : ":" + baseSuffix
                yield prefix + suffix
            }
            case TestFramework.JUNIT_JUPITER, TestFramework.KOTLIN_TEST -> {
                def prefix = Strings.isNullOrEmpty(basePrefix) ? ":" : ":" + basePrefix
                def suffix = Strings.isNullOrEmpty(baseSuffix) ? "" : ":" + baseSuffix + "()"
                yield prefix + suffix
            }
            case TestFramework.CUSTOM -> testPath
            default -> throw new IllegalArgumentException("Unknown test framework: " + testFramework)
        }
    }

    private java.nio.file.Path diskPathForTestPath(String frameworkTestPath) {
        String processedPath = Strings.isNullOrEmpty(frameworkTestPath) ? "index.html" : GenericHtmlTestReportGenerator.getFilePath(Path.path(frameworkTestPath))
        htmlReportDirectory.toPath().resolve(processedPath)
    }

    private static class HtmlTestPathExecutionResult implements TestPathExecutionResult {
        private static Map<String, Element> getTabs(Element base) {
            def container = base.selectFirst('.tab-container')
            def tabs = container.select('> .tab')
            def tabNames = container.select('> .tabLinks > li')
            assert tabs.size() == tabNames.size()
            Map<String, Element> result = new LinkedHashMap<>();
            for (int i = 0; i < tabs.size(); i++) {
                def tabName = tabNames.get(i).text()
                assert !result.containsKey(tabName) : "Duplicate tab name: " + tabName
                result[tabName] = tabs.get(i)
            }
            return result
        }

        private final List<String> rootNames = []
        private final List<String> rootDisplayNames = []
        private final ListMultimap<String, Element> rootAndRunElements = LinkedListMultimap.create()

        HtmlTestPathExecutionResult(File htmlFile) {
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
            return new HtmlTestPathRootExecutionResult(singleRoot.first(), rootDisplayNames.first())
        }

        @Override
        TestPathRootExecutionResult onlyOneRootAndRun(int runNumber) {
            List<Element> singleRoot = getSingleRoot()
            return new HtmlTestPathRootExecutionResult(singleRoot.get(runNumber - 1), rootDisplayNames.first())
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
            return new HtmlTestPathRootExecutionResult(runs.first(), rootDisplayNames[index])
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
            return new HtmlTestPathRootExecutionResult(runs.get(runNumber - 1), rootDisplayNames[index])
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
        private final Element html
        private final String displayName
        private Multimap<String, TestInfo> testsExecuted = LinkedListMultimap.create()
        private Multimap<String, TestInfo> testsSucceeded = LinkedListMultimap.create()
        private Multimap<String, TestInfo> testsFailures = LinkedListMultimap.create()
        private Multimap<String, TestInfo> testsSkipped = LinkedListMultimap.create()

        HtmlTestPathRootExecutionResult(Element html, String displayName) {
            this.html = html
            this.displayName = displayName
            extractCases()
        }

        private void extractCases() {
            extractTestCaseTo("tr > td.success:eq(0)", testsSucceeded)
            extractTestCaseTo("tr > td.failures:eq(0)", testsFailures)
            extractTestCaseTo("tr > td.skipped:eq(0)", testsSkipped)
        }

        private extractTestCaseTo(String cssSelector, Multimap<String, TestInfo> target) {
            html.select(cssSelector).each {
                def testDisplayName = it.text().trim()
                def testName = hasNameColumn() ? it.nextElementSibling().text().trim() : testDisplayName
                def testInfo = new TestInfo(testName, testDisplayName)
                testsExecuted.put(testName, testInfo)
                target.put(testName, testInfo)
            }
        }

        private boolean hasNameColumn() {
            return html.select('tr > th').size() == 7
        }

        @Override
        TestPathRootExecutionResult assertOnlyChildrenExecuted(String... testNames) {
            def executedAndNotSkipped = Multisets.difference(testsExecuted.keys(), testsSkipped.keys())
            assertThat("in " + displayName, executedAndNotSkipped, equalTo(ImmutableMultiset.copyOf(testNames)))
            return this
        }

        @Override
        TestPathRootExecutionResult assertChildrenExecuted(String... testNames) {
            def executedAndNotSkipped = Multisets.difference(testsExecuted.keys(), testsSkipped.keys())
            testNames.each {
                assertThat("in " + displayName, executedAndNotSkipped, hasItem(it))
            }
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
            assertThat("in " + displayName, testsSkipped.keys(), equalTo(ImmutableMultiset.copyOf(testNames)))
            return this
        }

        @Override
        int getSkippedChildCount() {
            return testsSkipped.size()
        }

        @Override
        TestPathRootExecutionResult assertChildrenFailed(String... testNames) {
            assertThat("in " + displayName, testsFailures.keys(), equalTo(ImmutableMultiset.copyOf(testNames)))
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
        TestPathRootExecutionResult assertMetadata(List<String> expectedKeys) {
            def metadataKeys = html.select('.metadata td.key').collect() { it.text() }
            assertThat("in " + displayName, metadataKeys, equalTo(expectedKeys))
            return this
        }

        @Override
        TestPathRootExecutionResult assertMetadata(LinkedHashMap<String, String> expectedMetadata) {
            def metadataKeys = html.select('.metadata td.key').collect() { it.text() }
            def metadataRenderedValues = html.select('.metadata td.value').collect { it.html()}
            def metadata = [metadataKeys, metadataRenderedValues].transpose().collectEntries { key, value -> [key, value] }
            assertThat("in " + displayName, metadata, equalTo(expectedMetadata))
            return this
        }
    }
}
