/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.fixtures.configurationcache

import groovy.json.JsonSlurper
import groovy.transform.PackageScope
import groovy.transform.ToString
import junit.framework.AssertionFailedError
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.executer.LogContent
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.jetbrains.annotations.VisibleForTesting

import javax.annotation.Nullable
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern
import java.util.stream.Collectors

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.endsWith
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.startsWith
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class ConfigurationCacheProblemsFixture {
    protected static final String CC_REPORT_HTML_FILE_NAME = "configuration-cache-report.html"

    protected final TestFile rootDir

    ConfigurationCacheProblemsFixture(File rootDir) {
        this.rootDir = rootDir instanceof TestFile ? rootDir : new TestFile(rootDir)
    }

    protected static HasConfigurationCacheProblemsSpec newProblemsSpec(
        Action<HasConfigurationCacheProblemsSpec> specAction
    ) {
        def spec = new HasConfigurationCacheProblemsSpec()
        specAction.execute(spec)
        spec.validateSpec()
        return spec
    }

    private static class ConfigurationCacheReportFixtureImpl extends ConfigurationCacheReportFixture {
        private final Map<String, Object> jsModel

        private ConfigurationCacheReportFixtureImpl(File reportFile) {
            jsModel = readJsModelFrom(reportFile)
        }

        @Override
        protected void assertContents(HasConfigurationCacheProblemsSpec spec) {
            assertProblemsHtmlReport(jsModel, spec)
            assertInputs(jsModel, spec)
            assertIncompatibleTasks(jsModel, spec)
        }
    }

    /**
     * Checks if a single configuration cache report is available at the standard location and returns a fixture to assert on it.
     * Fails if there is no report or there are multiple reports.
     */
    ConfigurationCacheReportFixture htmlReport() {
        // TODO(mlopatkin) what if the report is not present? htmlReport(String) allows it.
        return new ConfigurationCacheReportFixtureImpl(findReportFile())
    }

    /**
     * Creates a fixture to assert on the report based on the file URL written in the build output. The report may be absent.
     *
     * @param output the output of the build
     */
    ConfigurationCacheReportFixture htmlReport(String output) {
        def reportFile = resolveConfigurationCacheReport(rootDir, output)
        if (reportFile == null) {
            return new ConfigurationCacheReportFixture.NoReportFixtureImpl(rootDir)
        }

        return new ConfigurationCacheReportFixtureImpl(reportFile)
    }


    protected static Matcher<String> failureDescriptionMatcherForProblems(HasConfigurationCacheProblemsSpec spec) {
        return buildMatcherForProblemsFailureDescription(
            "Configuration cache problems found in this build.",
            spec
        )
    }

    protected static Matcher<String> failureDescriptionMatcherForTooManyProblems(HasConfigurationCacheProblemsSpec spec) {
        return buildMatcherForProblemsFailureDescription(
            "Maximum number of configuration cache problems has been reached.\n" +
                "This behavior can be adjusted. " +
                new DocumentationRegistry().getDocumentationRecommendationFor("on this", "configuration_cache_enabling", "config_cache:usage:max_problems"),
            spec
        )
    }

    private static Matcher<String> buildMatcherForProblemsFailureDescription(
        String message,
        HasConfigurationCacheProblemsSpec spec
    ) {
        return new BaseMatcher<String>() {
            @Override
            boolean matches(Object item) {
                if (!item.toString().contains(message)) {
                    return false
                }
                assertHasConsoleSummary(item.toString(), spec)
                return true
            }

            @Override
            void describeTo(Description description) {
                description.appendText("contains expected problems")
            }
        }
    }

    protected static void assertNoProblemsSummary(String text) {
        assertThat(text, not(containsString("configuration cache problem")))
    }

    protected static void assertHasConsoleSummary(String text, HasConfigurationCacheProblemsSpec spec) {
        if (spec.checkReportProblems) {
            // At this time, message expectations are either console-compatible or report-compatible.
            // Only the former are prefixed by location information ("Build file 'build.gradle': line...: <core-message>" or "Task `:foo` of type `Bar`: <core-message>").
            // When report problems are to be checked, then assert(Result)HtmlReportHasProblems should be used directly.
            throw new UnsupportedOperationException("content expectations for HTML report must be verified via #assertResultHtmlReportHasProblems or #assertFailureHtmlReportHasProblems")
        }

        def uniqueCount = spec.uniqueProblems.size()
        def totalCount = spec.totalProblemsCount ?: uniqueCount

        def summary = extractSummary(text)
        assert summary.totalProblems == totalCount
        assert summary.uniqueProblems == uniqueCount
        assert summary.messages.size() == spec.uniqueProblems.size()
        for (int i in spec.uniqueProblems.indices) {
            assert spec.uniqueProblems[i].matches(summary.messages[i])
        }
    }

    protected static void assertInputs(
        Map<String, Object> jsModel,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertItems('input', jsModel, spec.inputs)
    }

    protected static void assertIncompatibleTasks(
        Map<String, Object> jsModel,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertItems('incompatibleTask', jsModel, spec.incompatibleTasks)
    }

    private static void assertItems(
            String kind,
            Map<String, Object> jsModel,
            ItemSpec spec
        ) {
        if (spec == ItemSpec.IGNORING) {
            return
        }

        List<Matcher<String>> expectedItems = spec instanceof ItemSpec.ExpectingSome
            ? spec.itemMatchers.collect()
            : []


        List<Map<String, Object>> items = (jsModel.diagnostics as List<Map<String, Object>>).findAll { it[kind] != null }
        List<String> unexpectedItems = items.collect { formatItemForAssert(it, kind) }.reverse()
        for (int i in expectedItems.indices.reverse()) {
            def expectedItem = expectedItems[i]
            for (int j in unexpectedItems.indices) {
                if (expectedItem.matches(unexpectedItems[j])) {
                    expectedItems.removeAt(i)
                    unexpectedItems.removeAt(j)
                    break
                }
            }
        }
        if (!(spec instanceof ItemSpec.IgnoreUnexpected)) {
            assert unexpectedItems.isEmpty(): "Unexpected '$kind' items $unexpectedItems found in the report, expecting $expectedItems"
        }
        assert expectedItems.isEmpty(): "Expecting $expectedItems in the report, found $unexpectedItems"
    }

    private static String formatItemForAssert(Map<String, Object> item, String kind) {
        def trace = formatTrace(item['trace'][0])
        List<Map<String, Object>> itemFragments = item[kind]
        def message = formatStructuredMessage(itemFragments)
        "${trace}: ${message}"
    }

    private static String formatStructuredMessage(List<Map<String, Object>> fragments) {
        fragments.collect {
            // See StructuredMessage.Fragment
            it['text'] ?: "'${it['name']}'"
        }.join('')
    }

    private static String formatTrace(Map<String, Object> trace) {
        def kind = trace['kind']
        switch (kind) {
            case "Task": return trace['path']
            case "Bean": return trace['type']
            case "Field": return trace['name']
            case "InputProperty": return trace['name']
            case "OutputProperty": return trace['name']
                // Build file 'build.gradle'
            case "BuildLogic": return trace['location'].toString().capitalize()
            case "BuildLogicClass": return trace['type']
            default: return "Gradle runtime"
        }
    }

    protected static void assertProblemsHtmlReport(
        Map<String, Object> jsModel,
        HasConfigurationCacheProblemsSpec spec
    ) {
        def totalProblemCount = spec.totalProblemsCount ?: spec.uniqueProblems.size()
        def problemsWithStackTraceCount = spec.problemsWithStackTraceCount == null ? totalProblemCount : spec.problemsWithStackTraceCount
        assert (spec.totalProblemsCount != null ||
            spec.problemsWithStackTraceCount != null ||
            !spec.uniqueProblems.empty ||
            spec.incompatibleTasks instanceof ItemSpec.ExpectingSome ||
            spec.inputs instanceof ItemSpec.ExpectingSome):
                "The spec suggests the report shouldn't be generated but it was"

        doAssertProblemsHtmlReport(
            jsModel,
            totalProblemCount,
            spec.uniqueProblems,
            problemsWithStackTraceCount,
            spec.checkReportProblems
        )
    }

    private static void doAssertProblemsHtmlReport(
        def jsModel,
        int totalProblemCount,
        List<Matcher> uniqueProblems,
        int problemsWithStackTraceCount,
        boolean checkReportProblems
    ) {
        assertThat(
            "HTML report JS model has wrong number of total problem(s)",
            numberOfProblemsIn(jsModel),
            equalTo(totalProblemCount)
        )
        assertThat(
            "HTML report JS model has wrong number of problem(s) with stacktrace",
            numberOfProblemsWithStacktraceIn(jsModel),
            equalTo(problemsWithStackTraceCount)
        )
        if (checkReportProblems) {
            def problemMessages = problemMessagesIn(jsModel).unique()
            for (int i in uniqueProblems.indices) {
                // note that matchers for problem messages in report don't contain location prefixes
                assert uniqueProblems[i].matches(problemMessages[i]) : "Expected problem at #$i to be ${uniqueProblems[i]}, but was: ${problemMessages[i]}"
            }
        }
    }

    private static Map<String, Object> readJsModelFrom(File reportFile) {
        assertTrue("HTML report HTML file '$reportFile' not found", reportFile.isFile())

        // ConfigurationCacheReport ensures the pure json model can be read
        // by looking for `// begin-report-data` and `// end-report-data`
        def jsonText = linesBetween(reportFile, '// begin-report-data', '// end-report-data')
        assert jsonText: "malformed report file"
        new JsonSlurper().parseText(jsonText) as Map<String, Object>
    }

    private static String linesBetween(File file, String beginLine, String endLine) {
        return file.withReader('utf-8') { reader ->
            reader.lines().iterator()
                .dropWhile { it != beginLine }
                .drop(1)
                .takeWhile { it != endLine }
                .collect()
                .join('\n')
        }
    }

    File findReportFile() {
        return resolveSingleConfigurationCacheReport(rootDir)
    }

    private static TestFile resolveSingleConfigurationCacheReport(TestFile rootDir) {
        TestFile reportsDir = rootDir.file("build/reports/configuration-cache")

        assert reportsDir.exists():
            "Configuration cache report directory '$reportsDir' not found"

        List<TestFile> reportFiles = Files.walk(reportsDir.toPath()).withCloseable { stream ->
            stream
                .filter { it.fileName.toString() == CC_REPORT_HTML_FILE_NAME }
                .map { new TestFile(it.toString()) }
                .sorted()
                .collect(Collectors.toList())
        }

        assert reportFiles.size() > 0:
            "No report file found under $reportsDir"
        assert reportFiles.size() == 1:
            "Multiple report files (${reportFiles.size()}) found under $reportsDir in ${reportFiles.collect { it.parentFile.relativizeFrom(reportsDir) }.join(", ") }"
        return reportFiles.first()
    }

    @Nullable
    static TestFile resolveConfigurationCacheReport(File rootDir, String output) {
        def baseDirUri = clickableUrlFor(rootDir)
        def pattern = Pattern.compile("^See the complete report at (${Pattern.quote(baseDirUri)}.*/${Pattern.quote(CC_REPORT_HTML_FILE_NAME)})\$", Pattern.MULTILINE)
        def matcher = pattern.matcher(output)
        def reportFileUri = matcher.find() ? matcher.group(1) : null
        return reportFileUri ? new TestFile(Paths.get(URI.create(reportFileUri)).toFile().absoluteFile) : null
    }

    @Nullable
    static TestFile resolveConfigurationCacheReportDirectory(File rootDir, String output) {
        resolveConfigurationCacheReport(rootDir, output)?.parentFile
    }

    @VisibleForTesting
    static String clickableUrlFor(File file) {
        new ConsoleRenderer().asClickableFileUrl(file)
    }

    private static int numberOfProblemsIn(jsModel) {
        return (jsModel.diagnostics as List<Object>).count { it['problem'] != null }
    }

    /**
     * Makes a best effort to collect problem messages from the JS model.
     *
     * Does not include source locations, text is collected raw.
     */
    private static List<String> problemMessagesIn(jsModel) {
        return (jsModel.diagnostics as List<Object>)
            .findAll{ it['problem'] != null }
            .collect {
                it['problem']
                    .collect { (it as Map).values() }
                    .flatten().join()
            }
    }

    protected static int numberOfProblemsWithStacktraceIn(jsModel) {
        return (jsModel.diagnostics as List<Object>).count { it['problem'] != null && it['error']?.getAt('parts') != null }
    }

    protected static ProblemsSummary extractSummary(String text) {
        def headerPattern = Pattern.compile("(\\d+) (problems were|problem was) found (storing|reusing|updating) the configuration cache(, (\\d+) of which seem(s)? unique)?.*")
        def problemPattern = Pattern.compile("- (.*)")
        def docPattern = Pattern.compile(" {2}\\QSee https://docs.gradle.org\\E.*")
        def tooManyProblemsPattern = Pattern.compile("plus (\\d+) more problems. Please see the report for details.")

        def output = LogContent.of(text)

        def match = output.splitOnFirstMatchingLine(headerPattern)
        if (match == null) {
            throw new AssertionFailedError("""Could not find problems summary in output:
${text}
""")
        }

        def summary = match.right
        def matcher = headerPattern.matcher(summary.first)
        assert matcher.matches()
        def totalProblems = matcher.group(1).toInteger()
        def expectedUniqueProblems = matcher.group(5)?.toInteger() ?: totalProblems
        summary = summary.drop(1)

        def problems = []
        for (int i = 0; i < expectedUniqueProblems; i++) {
            matcher = problemPattern.matcher(summary.first)
            if (!matcher.matches()) {
                matcher = tooManyProblemsPattern.matcher(summary.first)
                if (matcher.matches()) {
                    def remainder = matcher.group(1).toInteger()
                    if (i + remainder == expectedUniqueProblems) {
                        break
                    }
                }
                throw new AssertionFailedError("""Expected ${expectedUniqueProblems - i} more problem descriptions, found: ${summary.first}""")
            }
            def problem = matcher.group(1)
            problems.add(problem)
            summary = summary.drop(1)
            if (summary.first.matches(docPattern)) {
                // Documentation for the current problem, skip
                // TODO - fail when there is no documentation for a problem
                summary = summary.drop(1)
            }
        }

        return new ProblemsSummary(totalProblems, problems.size(), problems)
    }

    @ToString(includeNames = true)
    private static class ProblemsSummary {
        final int totalProblems
        final int uniqueProblems
        final List<String> messages

        ProblemsSummary(int totalProblems, int uniqueProblems, List<String> messages) {
            this.totalProblems = totalProblems
            this.uniqueProblems = uniqueProblems
            this.messages = messages
        }
    }
}

abstract class ItemSpec {

    abstract ItemSpec expect(Matcher<String> itemMatcher)

    ItemSpec expectPrefix(String prefix) {
        return expect(startsWith(prefix))
    }

    abstract ItemSpec expectNone()

    abstract ItemSpec ignoreUnexpected()

    static final ItemSpec IGNORING = new ItemSpec() {

        @Override
        ItemSpec expect(Matcher<String> itemMatcher) {
            new ExpectingSome().expect(itemMatcher)
        }

        @Override
        ItemSpec expectNone() {
            EXPECTING_NONE
        }

        @Override
        ItemSpec ignoreUnexpected() {
            new IgnoreUnexpected([])
        }
    }

    static final ItemSpec EXPECTING_NONE = new ItemSpec() {

        @Override
        ItemSpec expect(Matcher<String> itemMatcher) {
            throw new IllegalStateException("Already expecting no items, cannot expect $itemMatcher")
        }

        @Override
        ItemSpec expectNone() {
            this
        }

        @Override
        ItemSpec ignoreUnexpected() {
            throw new IllegalStateException("Already expecting no items, cannot ignore unexpected.")
        }
    }

    static class ExpectingSome extends ItemSpec {


        @Override
        String toString() {
            return itemMatchers.join(", ")
        }
        final List<Matcher<String>> itemMatchers

        ExpectingSome(List<Matcher<String>> itemMatchers = []) {
            this.itemMatchers = itemMatchers
        }

        @Override
        ItemSpec expect(Matcher<String> itemMatcher) {
            itemMatchers.add(itemMatcher)
            this
        }

        @Override
        ItemSpec expectNone() {
            throw new IllegalStateException("Already expecting $itemMatchers, cannot expect none")
        }

        @Override
        ItemSpec ignoreUnexpected() {
            return new IgnoreUnexpected(itemMatchers)
        }
    }

    static class IgnoreUnexpected extends ExpectingSome {

        IgnoreUnexpected(List<Matcher<String>> itemMatchers) {
            super(itemMatchers)
        }

        @Override
        ItemSpec ignoreUnexpected() {
            this
        }
    }
}

/**
 * Defines an expectation for the Configuration Cache outputs: what is printed on the console, what is in the report.
 */
class HasConfigurationCacheProblemsSpec {

    @PackageScope
    final List<Matcher<String>> uniqueProblems = []

    @PackageScope
    ItemSpec inputs = ItemSpec.IGNORING

    @PackageScope
    ItemSpec incompatibleTasks = ItemSpec.IGNORING

    /**
     * An expectation for the total number of reported problems (including non-unique instances).
     * {@code null} means that no expectation is defined.
     */
    @Nullable
    Integer totalProblemsCount

    /**
     * An expectation for the total number of reported problems with stack traces.
     * {@code null} means that no expectation is defined.
     */
    @Nullable
    Integer problemsWithStackTraceCount

    /**
     * Whether to check for problem messages in the report.
     *
     * Note that message expectations are either console or report compatible,
     * so it is incorrect to enable report problems and attempt to check console messages.
     */
    @PackageScope
    boolean checkReportProblems = false

    @PackageScope
    void validateSpec() {
        def totalCount = totalProblemsCount ?: uniqueProblems.size()
        if (totalCount < uniqueProblems.size()) {
            throw new IllegalArgumentException("Count of total problems can't be lesser than count of unique problems.")
        }
        if (this.problemsWithStackTraceCount != null) {
            if (this.problemsWithStackTraceCount < 0) {
                throw new IllegalArgumentException("Count of problems with stacktrace can't be negative.")
            }
            if (this.problemsWithStackTraceCount > totalCount) {
                throw new IllegalArgumentException("Count of problems with stacktrace can't be greater that count of total problems.")
            }
        }
    }

    @PackageScope
    boolean hasProblems() {
        return !uniqueProblems.isEmpty() || totalProblemsCount > 0
    }

    /**
     * Sets the expectation for displayed problems.
     * The number and order of actual problems must match the expected.
     * The expected problem message is actually a prefix, so the actual message can be longer.
     * <p>
     * Note that the message format differs between console and report, the former includes a location prefix.
     *
     * @param uniqueProblems the prefixes of the expected problem messages
     * @return this
     */
    HasConfigurationCacheProblemsSpec withUniqueProblems(String... uniqueProblems) {
        return withUniqueProblems(uniqueProblems as List)
    }

    /**
     * Sets the expectation for displayed problems.
     * The number and order of actual problems must match the expected.
     * The expected problem message is actually a prefix, so the actual message can be longer.
     * <p>
     * Note that the message format differs between console and report, the former includes a location prefix.
     *
     * @param uniqueProblems the prefixes of the expected problem messages
     * @return this
     */
    HasConfigurationCacheProblemsSpec withUniqueProblems(Iterable<String> uniqueProblems) {
        this.uniqueProblems.clear()
        uniqueProblems.each {
            withProblem(it)
        }
        return this
    }

    /**
     * Adds an expectation for a displayed problem.
     * The number and order of actual problems must match the expected.
     * The expected problem message is actually a prefix, so the actual message can be longer.
     * <p>
     * Note that the message format differs between console and report, the former includes a location prefix.
     *
     * @param problem the prefixes of the expected problem message
     * @return this
     */
    HasConfigurationCacheProblemsSpec withProblem(String problem) {
        return withProblem(startsWith(problem))
    }

    /**
     * Adds an expectation for a displayed problem.
     * The number and order of actual problems must match the expected.
     * This method allows an arbitrary predicate.
     * <p>
     * Note that the message format differs between console and report, the former includes a location prefix.
     *
     * @param problem the matcher for the problem message
     * @return this
     */
    HasConfigurationCacheProblemsSpec withProblem(Matcher<String> problem) {
        uniqueProblems.add(problem)
        return this
    }

    /**
     * Adds an expectation for a build configuration input to be present.
     * The order of inputs in the predicate doesn't matter.
     * This is not compatible with {@link #withNoInputs()}.
     * <p>
     * All inputs must be verified unless {@link #ignoringUnexpectedInputs()} is used.
     *
     * @param prefix the prefix of the input including the location
     * @return this
     */
    HasConfigurationCacheProblemsSpec withInput(String prefix) {
        inputs = inputs.expectPrefix(prefix)
        return this
    }

    /**
     * Adds an expectation that no build configuration are to be present.
     * This is not compatible with {@link #withInput(String)} or {@link #ignoringUnexpectedInputs()}.
     *
     * @return this
     */
    HasConfigurationCacheProblemsSpec withNoInputs() {
        inputs = inputs.expectNone()
        return this
    }

    /**
     * Allows inputs not verified with {@link #withInput(String)} to be present in the result.
     * This is not compatible with {@link #withNoInputs()}.
     *
     * @return this
     */
    HasConfigurationCacheProblemsSpec ignoringUnexpectedInputs() {
        inputs = inputs.ignoreUnexpected()
        return this
    }

    /**
     * Adds an expectation for an incompatible task to be reported.
     * The order of tasks doesn't matter.
     * <p>
     * All incompatible tasks must be verified.
     *
     * @param task the task path
     * @param reason the expected compatibility reason
     * @return this
     */
    HasConfigurationCacheProblemsSpec withIncompatibleTask(String task, String reason) {
        incompatibleTasks = incompatibleTasks.expect(allOf(startsWith("${task}: task '${task}' of type "), endsWith(reason)))
        return this
    }
}
