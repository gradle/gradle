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

package org.gradle.integtests.fixtures.instantexecution

import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.instantexecution.SystemProperties
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.ConfigureUtil
import org.hamcrest.Matcher

import javax.annotation.Nullable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import java.nio.file.Paths
import java.util.regex.Pattern

import static org.gradle.util.Matchers.normalizedLineSeparators
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.CoreMatchers.startsWith
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue


final class InstantExecutionProblemsFixture {

    protected static final String PROBLEMS_REPORT_HTML_FILE_NAME = "instant-execution-report.html"

    private final GradleExecuter executer
    private final File rootDir

    InstantExecutionProblemsFixture(GradleExecuter executer, File rootDir) {
        this.executer = executer
        this.rootDir = rootDir
    }

    void withFailOnProblems() {
        executer.withArgument("-D${SystemProperties.failOnProblems}=true")
    }

    void withDoNotFailOnProblems() {
        executer.withArgument("-D${SystemProperties.failOnProblems}=false")
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        String error,
        @DelegatesTo(value = HasInstantExecutionErrorSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasError(failure, error, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        String error,
        Action<HasInstantExecutionErrorSpec> specAction = {}
    ) {
        assertFailureHasError(failure, newErrorSpec(error, specAction))
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        HasInstantExecutionErrorSpec spec
    ) {
        spec.validateSpec()

        assertFailureDescription(failure, spec.rootCauseDescription, failureDescriptionMatcherForError(spec))

        if (spec.hasProblems()) {
            assertProblemsConsoleSummary(failure.output, spec)
            assertProblemsHtmlReport(failure.output, rootDir, spec)
        } else {
            assertNoConsoleLogSummaryIn(failure.output)
        }
    }

    HasInstantExecutionErrorSpec newErrorSpec(
        String error,
        @DelegatesTo(value = HasInstantExecutionErrorSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        return newErrorSpec(error, ConfigureUtil.configureUsing(specClosure))
    }

    HasInstantExecutionErrorSpec newErrorSpec(
        String error,
        Action<HasInstantExecutionErrorSpec> specAction = {}
    ) {
        def spec = new HasInstantExecutionErrorSpec(error)
        specAction.execute(spec)
        return spec
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        @DelegatesTo(value = HasInstantExecutionProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasProblems(failure, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        Action<HasInstantExecutionProblemsSpec> specAction = {}
    ) {
        assertFailureHasProblems(failure, newProblemsSpec(specAction))
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        HasInstantExecutionProblemsSpec spec
    ) {
        assertNoConsoleLogSummaryIn(failure.output)
        assertFailureDescription(failure, spec.rootCauseDescription, failureDescriptionMatcherForProblems(spec))
        assertProblemsConsoleSummary(failure.error, spec)
        assertProblemsHtmlReport(failure.error, rootDir, spec)
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        @DelegatesTo(value = HasInstantExecutionProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasTooManyProblems(failure, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        Action<HasInstantExecutionProblemsSpec> specAction = {}
    ) {
        assertFailureHasTooManyProblems(failure, newProblemsSpec(specAction))
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        HasInstantExecutionProblemsSpec spec
    ) {
        assertNoConsoleLogSummaryIn(failure.output)
        assertFailureDescription(failure, spec.rootCauseDescription, failureDescriptionMatcherForTooManyProblems(spec))
        assertProblemsConsoleSummary(failure.error, spec)
        assertProblemsHtmlReport(failure.error, rootDir, spec)
    }

    void assertResultHasProblems(
        ExecutionResult result,
        @DelegatesTo(value = HasInstantExecutionProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertResultHasProblems(result, ConfigureUtil.configureUsing(specClosure))
    }

    void assertResultHasProblems(
        ExecutionResult result,
        Action<HasInstantExecutionProblemsSpec> specAction = {}
    ) {
        assertResultHasProblems(result, newProblemsSpec(specAction))
    }

    void assertResultHasProblems(
        ExecutionResult result,
        HasInstantExecutionProblemsSpec spec
    ) {
        // assert !(result instanceof ExecutionFailure)
        assertProblemsConsoleSummary(result.output, spec)
        assertProblemsHtmlReport(result.output, rootDir, spec)
    }

    HasInstantExecutionProblemsSpec newProblemsSpec(
        @DelegatesTo(value = HasInstantExecutionProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        return newProblemsSpec(ConfigureUtil.configureUsing(specClosure))
    }

    HasInstantExecutionProblemsSpec newProblemsSpec(
        Action<HasInstantExecutionProblemsSpec> specAction = {}
    ) {
        def spec = new HasInstantExecutionProblemsSpec()
        specAction.execute(spec)
        return spec
    }

    private static Matcher<String> failureDescriptionMatcherForError(HasInstantExecutionErrorSpec spec) {
        return equalTo("Instant execution state could not be cached: ${spec.error}".toString())
    }

    private static Matcher<String> failureDescriptionMatcherForProblems(HasInstantExecutionProblemsSpec spec) {
        def uniqueCount = spec.uniqueProblems.size()
        def totalCount = spec.totalProblemsCount ?: uniqueCount
        def summaryHeader = problemsSummaryHeaderFor(totalCount, uniqueCount)
        return allOf(
            startsWith("Problems found while caching instant execution state.\nFailing because -D${SystemProperties.failOnProblems} is 'true'."),
            containsString(summaryHeader),
            containsString("See the complete report at file:"),
            containsString(PROBLEMS_REPORT_HTML_FILE_NAME)
        )
    }

    private static Matcher<String> failureDescriptionMatcherForTooManyProblems(HasInstantExecutionProblemsSpec spec) {
        def uniqueCount = spec.uniqueProblems.size()
        def totalCount = spec.totalProblemsCount ?: uniqueCount
        def summaryHeader = problemsSummaryHeaderFor(totalCount, uniqueCount)
        return allOf(
            startsWith("Maximum number of instant execution problems has been reached.\nThis behavior can be adjusted via -D${SystemProperties.maxProblems}=<integer>."),
            containsString(summaryHeader),
            containsString("See the complete report at file:"),
            containsString(PROBLEMS_REPORT_HTML_FILE_NAME)
        )
    }

    private static void assertNoConsoleLogSummaryIn(String text) {
        assertThat(text, not(containsString("instant execution problem")))
    }

    private static void assertFailureDescription(
        ExecutionFailure failure,
        @Nullable String rootCauseDescription = null,
        Matcher<String> failureMatcher
    ) {
        if (rootCauseDescription) {
            failure.assertHasDescription(rootCauseDescription)
            failure.assertThatCause(failureMatcher)
        } else {
            failure.assertThatDescription(failureMatcher)
        }
    }

    private static void assertProblemsConsoleSummary(String output, HasInstantExecutionProblemsSpec spec) {
        def totalCount = spec.totalProblemsCount ?: spec.uniqueProblems.size()
        assertProblemsConsoleSummary(output, totalCount, spec.uniqueProblems)
    }

    private static void assertProblemsConsoleSummary(String output, int totalProblemsCount, List<String> uniqueProblems) {
        assertProblemsSummaryHeaderInOutput(output, totalProblemsCount, uniqueProblems.size())
        assertUniqueProblemsInOutput(output, uniqueProblems)
    }

    private static void assertProblemsSummaryHeaderInOutput(String output, int totalProblems, int uniqueProblems) {
        if (totalProblems > 0 || uniqueProblems > 0) {
            def header = problemsSummaryHeaderFor(totalProblems, uniqueProblems)
            assertThat(output, containsNormalizedString(header))
        } else {
            assertThat(output, not(containsNormalizedString("instant execution problem")))
        }
    }

    private static void assertUniqueProblemsInOutput(String output, List<String> uniqueProblems) {
        def uniqueProblemsCount = uniqueProblems.size()
        def problems = uniqueProblems.collect { "- $it".toString() }
        def found = 0
        output.readLines().eachWithIndex { String line, int idx ->
            if (problems.remove(line.trim())) {
                found++
                return
            }
        }
        assert problems.empty, "Expected ${uniqueProblemsCount} unique problems, found ${found} unique problems, remaining:\n${problems.collect { " - $it" }.join("\n")}"
    }


    protected static String problemsSummaryHeaderFor(int totalProblems, int uniqueProblems) {
        return "${totalProblems} instant execution problem${totalProblems >= 2 ? 's were' : ' was'} found, " +
            "${uniqueProblems} of which seem${uniqueProblems >= 2 ? '' : 's'} unique."
    }

    protected static void assertProblemsHtmlReport(
        String output,
        File rootDir,
        HasInstantExecutionProblemsSpec spec
    ) {
        assertProblemsHtmlReport(
            rootDir,
            output,
            spec.totalProblemsCount ? spec.totalProblemsCount : spec.uniqueProblems.size(),
            spec.uniqueProblems.size(),
            spec.problemsWithStackTraceCount
        )
    }

    protected static void assertProblemsHtmlReport(
        File rootDir,
        String output,
        int totalProblemCount,
        int uniqueProblemCount,
        @Nullable Integer problemsWithStackTraceCount
    ) {
        def expectReport = totalProblemCount > 0 || uniqueProblemCount > 0
        def reportDir = resolveInstantExecutionReportDirectory(rootDir, output)
        if (expectReport) {
            assertThat("HTML report URI not found", reportDir, notNullValue())
            assertTrue("HTML report directory not found '$reportDir'", reportDir.isDirectory())
            def htmlFile = reportDir.file(PROBLEMS_REPORT_HTML_FILE_NAME)
            def jsFile = reportDir.file('instant-execution-report-data.js')
            assertTrue("HTML report HTML file not found in '$reportDir'", htmlFile.isFile())
            assertTrue("HTML report JS model not found in '$reportDir'", jsFile.isFile())
            assertThat(
                "HTML report JS model has wrong number of total problem(s)",
                numberOfProblemsIn(jsFile),
                equalTo(totalProblemCount)
            )
            if (problemsWithStackTraceCount != null) {
                assertThat(
                    "HTML report JS model has wrong number of problem(s) with stacktrace",
                    numberOfProblemsWithStacktraceIn(jsFile),
                    equalTo(problemsWithStackTraceCount)
                )
            }
        } else {
            assertThat("Unexpected HTML report URI found", reportDir, nullValue())
        }
    }

    @Nullable
    private static TestFile resolveInstantExecutionReportDirectory(File rootDir, String output) {
        def baseDirUri = clickableUrlFor(new File(rootDir, "build/reports/instant-execution"))
        def pattern = Pattern.compile("See the complete report at (${baseDirUri}.*)$PROBLEMS_REPORT_HTML_FILE_NAME")
        def reportDirUri = output.readLines().findResult { line ->
            def matcher = pattern.matcher(line)
            matcher.matches() ? matcher.group(1) : null
        }
        return reportDirUri ? new TestFile(Paths.get(URI.create(reportDirUri)).toFile().absoluteFile) : null
    }

    private static String clickableUrlFor(File file) {
        new ConsoleRenderer().asClickableFileUrl(file)
    }

    private static int numberOfProblemsIn(File jsFile) {
        newJavaScriptEngine().with {
            eval(jsFile.text)
            eval("instantExecutionProblems().length") as int
        }
    }

    protected static int numberOfProblemsWithStacktraceIn(File jsFile) {
        newJavaScriptEngine().with {
            eval(jsFile.text)
            eval("instantExecutionProblems().filter(function(problem) { return problem['error'] != null; }).length") as int
        }
    }

    private static ScriptEngine newJavaScriptEngine() {
        new ScriptEngineManager().getEngineByName("JavaScript")
    }

    protected static Matcher<String> containsNormalizedString(String string) {
        return normalizedLineSeparators(containsString(string))
    }
}

final class HasInstantExecutionErrorSpec extends HasInstantExecutionProblemsSpec {

    @PackageScope
    String error

    @PackageScope
    HasInstantExecutionErrorSpec(String error) {
        this.error = error
    }
}


class HasInstantExecutionProblemsSpec {

    @Nullable
    @PackageScope
    String rootCauseDescription

    @PackageScope
    final List<String> uniqueProblems = []

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
        return !uniqueProblems.isEmpty()
    }

    HasInstantExecutionProblemsSpec withRootCauseDescription(String rootCauseDescription) {
        this.rootCauseDescription = rootCauseDescription
        return this
    }

    HasInstantExecutionProblemsSpec withUniqueProblems(String... uniqueProblems) {
        return withUniqueProblems(uniqueProblems as List)
    }

    HasInstantExecutionProblemsSpec withUniqueProblems(Iterable<String> uniqueProblems) {
        this.uniqueProblems.clear()
        this.uniqueProblems.addAll(uniqueProblems)
        return this
    }

    HasInstantExecutionProblemsSpec withTotalProblemsCount(int totalProblemsCount) {
        this.totalProblemsCount = totalProblemsCount
        return this
    }

    HasInstantExecutionProblemsSpec withProblemsWithStackTraceCount(int problemsWithStackTraceCount) {
        this.problemsWithStackTraceCount = problemsWithStackTraceCount
        return this
    }
}

