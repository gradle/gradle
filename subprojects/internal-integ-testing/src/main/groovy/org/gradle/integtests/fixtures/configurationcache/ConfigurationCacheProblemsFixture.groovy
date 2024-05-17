/*
 * Copyright 2020 the original author or authors.
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
import junit.framework.AssertionFailedError
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.LogContent
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ConfigureUtil
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

import javax.annotation.Nullable
import java.nio.file.Paths
import java.util.regex.Pattern

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.CoreMatchers.startsWith
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

final class ConfigurationCacheProblemsFixture {

    protected static final String PROBLEMS_REPORT_HTML_FILE_NAME = "configuration-cache-report.html"

    private final GradleExecuter executer
    private final File rootDir

    ConfigurationCacheProblemsFixture(GradleExecuter executer, File rootDir) {
        this.executer = executer
        this.rootDir = rootDir
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        String error,
        @DelegatesTo(value = HasConfigurationCacheErrorSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasError(failure, error, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        String error,
        Action<HasConfigurationCacheErrorSpec> specAction = {}
    ) {
        assertFailureHasError(failure, newErrorSpec(error, specAction))
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        HasConfigurationCacheErrorSpec spec
    ) {
        spec.validateSpec()

        assertFailureDescription(failure, failureDescriptionMatcherForError(spec))

        if (spec.hasProblems()) {
            assertHasConsoleSummary(failure.output, spec)
            assertProblemsHtmlReport(failure.output, rootDir, spec)
        } else {
            assertNoProblemsSummary(failure.output)
        }
    }

    HasConfigurationCacheErrorSpec newErrorSpec(
        String error,
        @DelegatesTo(value = HasConfigurationCacheErrorSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        return newErrorSpec(error, ConfigureUtil.configureUsing(specClosure))
    }

    HasConfigurationCacheErrorSpec newErrorSpec(
        String error,
        Action<HasConfigurationCacheErrorSpec> specAction = {}
    ) {
        def spec = new HasConfigurationCacheErrorSpec(error)
        specAction.execute(spec)
        return spec
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasProblems(failure, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertFailureHasProblems(failure, newProblemsSpec(specAction))
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertNoProblemsSummary(failure.output)
        assertFailureDescription(failure, failureDescriptionMatcherForProblems(spec))
        assertProblemsHtmlReport(failure.error, rootDir, spec)
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasTooManyProblems(failure, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertFailureHasTooManyProblems(failure, newProblemsSpec(specAction))
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertNoProblemsSummary(failure.output)
        assertFailureDescription(failure, failureDescriptionMatcherForTooManyProblems(spec))
        assertProblemsHtmlReport(failure.error, rootDir, spec)
    }

    void assertResultHasProblems(
        ExecutionResult result,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertResultHasProblems(result, ConfigureUtil.configureUsing(specClosure))
    }

    void assertResultHasProblems(
        ExecutionResult result,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertResultHasProblems(result, newProblemsSpec(specAction))
    }

    void assertResultHasProblems(
        ExecutionResult result,
        HasConfigurationCacheProblemsSpec spec
    ) {
        // assert !(result instanceof ExecutionFailure)
        if (spec.hasProblems()) {
            assertHasConsoleSummary(result.output, spec)
            assertProblemsHtmlReport(result.output, rootDir, spec)
        } else {
            assertNoProblemsSummary(result.output)
        }
        // TODO:bamboo avoid reading jsModel twice when asserting on problems AND inputs
        assertInputs(result.output, rootDir, spec.inputs)
    }

    HasConfigurationCacheProblemsSpec newProblemsSpec(
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        return newProblemsSpec(ConfigureUtil.configureUsing(specClosure))
    }

    HasConfigurationCacheProblemsSpec newProblemsSpec(
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        def spec = new HasConfigurationCacheProblemsSpec()
        specAction.execute(spec)
        return spec
    }

    private static Matcher<String> failureDescriptionMatcherForError(HasConfigurationCacheErrorSpec spec) {
        return equalTo("Configuration cache state could not be cached: ${spec.error}".toString())
    }

    private static Matcher<String> failureDescriptionMatcherForProblems(HasConfigurationCacheProblemsSpec spec) {
        return buildMatcherForProblemsFailureDescription(
            "Configuration cache problems found in this build.",
            spec
        )
    }

    private static Matcher<String> failureDescriptionMatcherForTooManyProblems(HasConfigurationCacheProblemsSpec spec) {
        return buildMatcherForProblemsFailureDescription(
            "Maximum number of configuration cache problems has been reached.\n" +
                "This behavior can be adjusted. " +
                new DocumentationRegistry().getDocumentationRecommendationFor("on this", "configuration_cache", "config_cache:usage:max_problems"),
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

    private static void assertNoProblemsSummary(String text) {
        assertThat(text, not(containsString("configuration cache problem")))
    }

    private static void assertFailureDescription(
        ExecutionFailure failure,
        Matcher<String> failureMatcher
    ) {
        failure.assertThatDescription(failureMatcher)
    }

    private static void assertHasConsoleSummary(String text, HasConfigurationCacheProblemsSpec spec) {
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

    private static void assertProblemsHtmlReport(
        String output,
        File rootDir,
        HasConfigurationCacheProblemsSpec spec
    ) {
        def totalProblemCount = spec.totalProblemsCount ?: spec.uniqueProblems.size()
        assertProblemsHtmlReport(
            rootDir,
            output,
            totalProblemCount,
            spec.uniqueProblems.size(),
            spec.problemsWithStackTraceCount == null ? totalProblemCount : spec.problemsWithStackTraceCount
        )
    }

    private static void assertInputs(
        String output,
        File rootDir,
        InputsSpec spec
    ) {
        if (spec == InputsSpec.IGNORING) {
            return
        }

        List<Matcher<String>> expectedInputs = spec instanceof InputsSpec.ExpectingSome
            ? spec.inputs.collect()
            : []

        def reportDir = resolveConfigurationCacheReportDirectory(rootDir, output)
        if (reportDir == null) {
            assertThat(
                "Expecting inputs but no report was found",
                expectedInputs,
                equalTo([])
            )
            return
        }

        Map<String, Object> jsModel = readJsModelFromReportDir(reportDir)
        List<Map<String, Object>> inputs = (jsModel.diagnostics as List<Map<String, Object>>).findAll { it['input'] != null }
        List<String> unexpectedInputs = inputs.collect { formatInputForAssert(it) }.reverse()
        for (int i in expectedInputs.indices.reverse()) {
            def expectedInput = expectedInputs[i]
            for (int j in unexpectedInputs.indices) {
                if (expectedInput.matches(unexpectedInputs[j])) {
                    expectedInputs.removeAt(i)
                    unexpectedInputs.removeAt(j)
                    break
                }
            }
        }
        if (!(spec instanceof InputsSpec.IgnoreUnexpected)) {
            assert unexpectedInputs.isEmpty() : "Unexpected inputs $unexpectedInputs found in the report, expecting $expectedInputs"
        }
        assert expectedInputs.isEmpty() : "Expecting $expectedInputs in the report, found $unexpectedInputs"
    }

    static String formatInputForAssert(Map<String, Object> input) {
        "${formatTrace(input['trace'][0])}: ${formatStructuredMessage(input['input'])}"
    }

    private static String formatStructuredMessage(List<Map<String, Object>> fragments) {
        fragments.collect {
            // See StructuredMessage.Fragment
            it['text'] ?: "'${it['name']}'"
        }.join('')
    }

    private static String formatTrace(Map<String, Object> trace) {
        switch (trace['kind']) {
            case "Task": return trace['path']
            case "Bean": return trace['type']
            case "Field": return trace['name']
            case "InputProperty": return trace['name']
            case "OutputProperty": return trace['name']
            case "BuildLogic": return trace['location'].toString().capitalize()
            case "BuildLogicClass": return trace['type']
            default: return "Gradle runtime"
        }
    }

    private static void assertProblemsHtmlReport(
        File rootDir,
        String output,
        int totalProblemCount,
        int uniqueProblemCount,
        int problemsWithStackTraceCount
    ) {
        def expectReport = totalProblemCount > 0 || uniqueProblemCount > 0
        def reportDir = resolveConfigurationCacheReportDirectory(rootDir, output)
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
        } else {
            assertThat("Unexpected HTML report URI found", reportDir, nullValue())
        }
    }

    private static Map<String, Object> readJsModelFromReportDir(TestFile reportDir) {
        assertThat("HTML report URI not found", reportDir, notNullValue())
        assertTrue("HTML report directory not found '$reportDir'", reportDir.isDirectory())
        def htmlFile = reportDir.file(PROBLEMS_REPORT_HTML_FILE_NAME)
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

    private static String clickableUrlFor(File file) {
        new ConsoleRenderer().asClickableFileUrl(file)
    }

    private static int numberOfProblemsIn(jsModel) {
        return (jsModel.diagnostics as List<Object>).count { it['problem'] != null }
    }

    protected static int numberOfProblemsWithStacktraceIn(jsModel) {
        return (jsModel.diagnostics as List<Object>).count { it['error'] != null }
    }

    private static ProblemsSummary extractSummary(String text) {
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

abstract class InputsSpec {

    abstract InputsSpec expect(Matcher<String> input)

    abstract InputsSpec expectNone()

    abstract InputsSpec ignoreUnexpected()

    static final InputsSpec IGNORING = new InputsSpec() {

        @Override
        InputsSpec expect(Matcher<String> input) {
            new ExpectingSome().expect(input)
        }

        @Override
        InputsSpec expectNone() {
            EXPECTING_NONE
        }

        @Override
        InputsSpec ignoreUnexpected() {
            new IgnoreUnexpected([])
        }
    }

    static final InputsSpec EXPECTING_NONE = new InputsSpec() {

        @Override
        InputsSpec expect(Matcher<String> input) {
            throw new IllegalStateException("Already expecting no inputs, cannot expect $input")
        }

        @Override
        InputsSpec expectNone() {
            this
        }

        @Override
        InputsSpec ignoreUnexpected() {
            throw new IllegalStateException("Already expecting no inputs, cannot ignore unexpected.")
        }
    }

    static class ExpectingSome extends InputsSpec {

        final List<Matcher<String>> inputs

        ExpectingSome(List<Matcher<String>> inputs = []) {
            this.inputs = inputs
        }

        @Override
        InputsSpec expect(Matcher<String> input) {
            inputs.add(input)
            this
        }

        @Override
        InputsSpec expectNone() {
            throw new IllegalStateException("Already expecting $inputs, cannot expect none")
        }

        @Override
        InputsSpec ignoreUnexpected() {
            return new IgnoreUnexpected(inputs)
        }
    }

    static class IgnoreUnexpected extends ExpectingSome {

        IgnoreUnexpected(List<Matcher<String>> inputs) {
            super(inputs)
        }

        @Override
        InputsSpec ignoreUnexpected() {
            this
        }
    }
}

class HasConfigurationCacheProblemsSpec {

    @PackageScope
    final List<Matcher<String>> uniqueProblems = []

    @PackageScope
    InputsSpec inputs = InputsSpec.IGNORING

    @Nullable
    @PackageScope
    Integer totalProblemsCount

    @Nullable
    @PackageScope
    Integer problemsWithStackTraceCount

    @PackageScope
    void validateSpec() {
        def totalCount = totalProblemsCount ?: uniqueProblems.size()
        if (totalCount < uniqueProblems.size()) {
            throw new IllegalArgumentException("Count of total problems can't be lesser than count of unique problems.")
        }
        if (problemsWithStackTraceCount != null) {
            if (problemsWithStackTraceCount < 0) {
                throw new IllegalArgumentException("Count of problems with stacktrace can't be negative.")
            }
            if (problemsWithStackTraceCount > totalCount) {
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
        inputs = inputs.expect(startsWith(prefix))
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
}
