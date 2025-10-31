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
import org.gradle.util.internal.ConfigureUtil
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.jetbrains.annotations.VisibleForTesting

import javax.annotation.Nullable
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.endsWith
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.CoreMatchers.startsWith
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class ConfigurationCacheProblemsFixture {
    protected static final String PROBLEMS_REPORT_HTML_FILE_NAME = "configuration-cache-report.html"

    protected final TestFile rootDir

    ConfigurationCacheProblemsFixture(File rootDir) {
        this.rootDir = rootDir instanceof TestFile ? rootDir : new TestFile(rootDir)
    }

    static HasConfigurationCacheProblemsSpec newProblemsSpec(
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure = {}
    ) {
        return newProblemsSpec(ConfigureUtil.configureUsing(specClosure))
    }

    protected static HasConfigurationCacheProblemsSpec newProblemsSpec(
        Action<HasConfigurationCacheProblemsSpec> specAction
    ) {
        def spec = new HasConfigurationCacheProblemsSpec()
        specAction.execute(spec)
        return spec
    }

    static HasConfigurationCacheErrorSpec newErrorSpec(
        String error,
        @DelegatesTo(value = HasConfigurationCacheErrorSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure = {}
    ) {
        return newErrorSpec(error, ConfigureUtil.configureUsing(specClosure))
    }

    static HasConfigurationCacheErrorSpec newErrorSpec(
        String error,
        Action<HasConfigurationCacheErrorSpec> specAction
    ) {
        def spec = new HasConfigurationCacheErrorSpec(error)
        specAction.execute(spec)
        return spec
    }

    void assertOutputHasError(String output, HasConfigurationCacheErrorSpec spec) {
        spec.validateSpec()

        if (spec.hasProblems()) {
            assertHasConsoleSummary(output, spec)
            assertProblemsHtmlReport(output, rootDir, spec)
        } else {
            assertNoProblemsSummary(output)
        }
    }

    void assertHtmlReportHasProblems(
        String output,
        HasConfigurationCacheProblemsSpec spec
    ) {
        def reportDir = resolveConfigurationCacheReportDirectory(rootDir, output)
        assertHtmlReportHasProblems(reportDir, spec)
    }

    void assertHtmlReportHasProblems(
        String output,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure = {}
    ) {
        assertHtmlReportHasProblems(output, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    void assertHtmlReportHasProblems(
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure = {}
    ) {
        def reportDir = findReportDir()
        assertHtmlReportHasProblems(reportDir, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    void assertHtmlReportHasProblems(
        File reportDir,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure = {}
    ) {
        assertHtmlReportHasProblems(reportDir, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    /**
     * Asserts that a report exists in the current project but has no problems.
     */
    void assertHtmlReportHasNoProblems() {
        def reportDir = findReportDir()
        assertHtmlReportHasNoProblems(reportDir)
    }

    /**
     * Asserts that a report exists in the given dir but has no problems.
     */
    void assertHtmlReportHasNoProblems(
        File reportDir
    ) {
        assertHtmlReportHasProblems(reportDir, {
            totalProblemsCount = 0
        })
    }

    void assertHtmlReportHasProblems(File reportDir, HasConfigurationCacheProblemsSpec spec) {
        spec.checkReportProblems = true
        assertProblemsHtmlReport(reportDir, spec)
        assertInputs(reportDir, spec)
        assertIncompatibleTasks(reportDir, spec)
    }

    protected static Matcher<String> failureDescriptionMatcherForError(HasConfigurationCacheErrorSpec spec) {
        return equalTo("Configuration cache state could not be cached: ${spec.error}".toString())
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

    protected static void assertProblemsHtmlReport(
        String output,
        File rootDir,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertProblemsHtmlReport(
            resolveConfigurationCacheReportDirectory(rootDir, output),
            spec
        )
    }

    protected static void assertInputs(
        String output,
        File rootDir,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertItems('input', output, rootDir, spec.inputs)
    }

    protected static void assertInputs(
        File reportDir,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertItems('input', reportDir, spec.inputs)
    }

    protected static void assertIncompatibleTasks(
        String output,
        File rootDir,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertItems('incompatibleTask', output, rootDir, spec.incompatibleTasks)
    }

    protected static void assertIncompatibleTasks(
        File reportDir,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertItems('incompatibleTask', reportDir, spec.incompatibleTasks)
    }

    private static void assertItems(
        String kind,
        String output,
        File rootDir,
        ItemSpec spec
    ) {
        def reportDir = resolveConfigurationCacheReportDirectory(rootDir, output)
        assertItems(kind, reportDir, spec)
    }

    private static void assertItems(
            String kind,
            File reportDir,
            ItemSpec spec
        ) {

        if (spec == ItemSpec.IGNORING) {
            return
        }

        List<Matcher<String>> expectedItems = spec instanceof ItemSpec.ExpectingSome
            ? spec.itemMatchers.collect()
            : []

        if (reportDir == null) {
            assertThat(
                "Expecting '$kind' items but no report was found",
                expectedItems,
                equalTo([])
            )
            return
        }

        Map<String, Object> jsModel = readJsModelFromReportDir(reportDir)
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
        File reportDir,
        HasConfigurationCacheProblemsSpec spec
    ) {
        def totalProblemCount = spec.totalProblemsCount ?: spec.uniqueProblems.size()
        def problemsWithStackTraceCount = spec.problemsWithStackTraceCount == null ? totalProblemCount : spec.problemsWithStackTraceCount
        boolean shouldHaveReport = spec.totalProblemsCount != null ||
            problemsWithStackTraceCount != null ||
            !spec.uniqueProblems.empty ||
            spec.incompatibleTasks instanceof ItemSpec.ExpectingSome ||
            spec.inputs instanceof ItemSpec.ExpectingSome
        doAssertProblemsHtmlReport(
            reportDir,
            totalProblemCount,
            spec.uniqueProblems,
            problemsWithStackTraceCount,
            shouldHaveReport,
            spec.checkReportProblems
        )
    }

    private static void doAssertProblemsHtmlReport(
        File reportDir,
        int totalProblemCount,
        List<Matcher> uniqueProblems,
        int problemsWithStackTraceCount,
        boolean expectReport,
        boolean checkReportProblems
    ) {
        if (expectReport) {
            Map<String, Object> jsModel = readJsModelFromReportDir(reportDir)
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
        } else {
            assertThat("Unexpected HTML report URI found", reportDir?.with { it.directory ? it : null }, nullValue())
        }
    }

    private static Map<String, Object> readJsModelFromReportDir(File reportDir) {
        assertThat("HTML report URI not found", reportDir, notNullValue())
        assertTrue("HTML report directory not found '$reportDir'", reportDir.isDirectory())
        def htmlFile = new File(reportDir, PROBLEMS_REPORT_HTML_FILE_NAME)
        assertTrue("HTML report HTML file not found in '$reportDir'", htmlFile.isFile())
        Map<String, Object> jsModel = readJsModelFrom(htmlFile)
        jsModel
    }

    private static Map<String, Object> readJsModelFrom(File reportFile) {
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

    File findReportDir() {
        return resolveSingleConfigurationCacheReportDir(rootDir)
    }

    private static TestFile resolveSingleConfigurationCacheReportDir(TestFile rootDir) {
        TestFile reportsDir = rootDir.file("build/reports/configuration-cache")
        assert reportsDir.exists() : "Configuration cache report directory not found at $reportsDir"
        List<TestFile> reportDirs = []
        Files.walkFileTree(reportsDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString() == "configuration-cache-report.html") {
                    reportDirs += new TestFile(file.parent.toString())
                }
                return FileVisitResult.CONTINUE
            }
        })

        assert reportDirs.size() > 0 : "No report file found under $reportsDir"
        assert reportDirs.size() == 1 : "Multiple report files (${reportDirs.size()}) found under $reportsDir - ${reportDirs.sort().collect { it.relativizeFrom(reportsDir) }.join(", ") }"
        return reportDirs[0]
    }

    @Nullable
    static TestFile resolveConfigurationCacheReport(File rootDir, String output) {
        resolveConfigurationCacheReportDirectory(rootDir, output)?.file(PROBLEMS_REPORT_HTML_FILE_NAME)
    }

    @Nullable
    static TestFile resolveConfigurationCacheReportDirectory(File rootDir, String output) {
        def baseDirUri = clickableUrlFor(rootDir)
        def pattern = Pattern.compile("^See the complete report at (${Pattern.quote(baseDirUri)}.*/)${Pattern.quote(PROBLEMS_REPORT_HTML_FILE_NAME)}\$", Pattern.MULTILINE)
        def matcher = pattern.matcher(output)
        def reportDirUri = matcher.find() ? matcher.group(1) : null
        return reportDirUri ? new TestFile(Paths.get(URI.create(reportDirUri)).toFile().absoluteFile) : null
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

final class HasConfigurationCacheErrorSpec extends HasConfigurationCacheProblemsSpec {

    @PackageScope
    String error

    @PackageScope
    HasConfigurationCacheErrorSpec(String error) {
        this.error = error
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

class HasConfigurationCacheProblemsSpec {

    @PackageScope
    final List<Matcher<String>> uniqueProblems = []

    @PackageScope
    ItemSpec inputs = ItemSpec.IGNORING

    @PackageScope
    ItemSpec incompatibleTasks = ItemSpec.IGNORING

    @Nullable
    @PackageScope
    Integer totalProblemsCount

    @Nullable
    @PackageScope
    Integer problemsWithStackTraceCount

    /**
     * Whether to check for problem messages in the report.
     *
     * Note that message expectations are either console or report compatible,
     * so it is incorrect to enable report problems and attempt to check console messages.
     */
    @PackageScope
    Boolean checkReportProblems = false

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

    HasConfigurationCacheProblemsSpec withUniqueProblems(String... uniqueProblems) {
        return withUniqueProblems(uniqueProblems as List)
    }

    HasConfigurationCacheProblemsSpec withUniqueProblems(Iterable<String> uniqueProblems) {
        this.uniqueProblems.clear()
        uniqueProblems.each {
            withProblem(it)
        }
        return this
    }

    HasConfigurationCacheProblemsSpec withProblem(String problem) {
        uniqueProblems.add(startsWith(problem))
        return this
    }

    HasConfigurationCacheProblemsSpec withProblem(Matcher<String> problem) {
        uniqueProblems.add(problem)
        return this
    }

    HasConfigurationCacheProblemsSpec withTotalProblemsCount(int totalProblemsCount) {
        this.totalProblemsCount = totalProblemsCount
        return this
    }

    HasConfigurationCacheProblemsSpec withProblemsWithStackTraceCount(int problemsWithStackTraceCount) {
        this.problemsWithStackTraceCount = problemsWithStackTraceCount
        return this
    }

    HasConfigurationCacheProblemsSpec withInput(String prefix) {
        inputs = inputs.expectPrefix(prefix)
        return this
    }

    HasConfigurationCacheProblemsSpec withNoInputs() {
        inputs = inputs.expectNone()
        return this
    }

    HasConfigurationCacheProblemsSpec ignoringUnexpectedInputs() {
        inputs = inputs.ignoreUnexpected()
        return this
    }

    HasConfigurationCacheProblemsSpec withIncompatibleTask(String task, String reason) {
        incompatibleTasks = incompatibleTasks.expect(allOf(startsWith("${task}: task '${task}' of type "), endsWith(reason)))
        return this
    }

    HasConfigurationCacheProblemsSpec ignoringUnexpectedIncompatibleTasks() {
        incompatibleTasks = incompatibleTasks.ignoreUnexpected()
        return this
    }
}
