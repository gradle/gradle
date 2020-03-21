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

import org.gradle.instantexecution.InstantExecutionErrorException
import org.gradle.instantexecution.InstantExecutionErrorsException
import org.gradle.instantexecution.InstantExecutionException
import org.gradle.instantexecution.InstantExecutionProblemException
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

import static org.gradle.util.Matchers.matchesRegexp
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

    void expectFailure(
        ExecutionResult result,
        Class<? extends InstantExecutionException> exceptionType,
        @DelegatesTo(value = InstantExecutionFailureSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        expectFailure(result, exceptionType, ConfigureUtil.configureUsing(specClosure))
    }

    void expectFailure(
        ExecutionResult result,
        Class<? extends InstantExecutionException> exceptionType,
        Action<InstantExecutionFailureSpec> specAction = {}
    ) {
        expectFailure(result, newFailureSpec(exceptionType, specAction))
    }

    void expectFailure(ExecutionResult result, InstantExecutionFailureSpec spec) {
        spec.assertOn(result, rootDir)
    }

    static InstantExecutionFailureSpec newFailureSpec(
        Class<? extends InstantExecutionException> exception,
        @DelegatesTo(value = InstantExecutionFailureSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        return newFailureSpec(exception, ConfigureUtil.configureUsing(specClosure))
    }

    static InstantExecutionFailureSpec newFailureSpec(
        Class<? extends InstantExecutionException> exception,
        Action<InstantExecutionFailureSpec> specAction = {}
    ) {
        def spec = new InstantExecutionFailureSpec(exception)
        specAction.execute(spec)
        return spec
    }

    void expectWarnings(
        ExecutionResult result,
        @DelegatesTo(value = InstantExecutionWarningsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        expectWarnings(result, ConfigureUtil.configureUsing(specClosure))
    }

    void expectWarnings(
        ExecutionResult result,
        Action<InstantExecutionWarningsSpec> specAction = {}
    ) {
        expectWarnings(result, newWarningsSpec(specAction))
    }

    void expectWarnings(ExecutionResult result, InstantExecutionWarningsSpec spec) {
        spec.assertOn(result, rootDir)
    }

    static InstantExecutionWarningsSpec newWarningsSpec(
        @DelegatesTo(value = InstantExecutionWarningsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        return newWarningsSpec(ConfigureUtil.configureUsing(specClosure))
    }

    static InstantExecutionWarningsSpec newWarningsSpec(
        Action<InstantExecutionWarningsSpec> specAction = {}
    ) {
        def spec = new InstantExecutionWarningsSpec()
        specAction.execute(spec)
        return spec
    }
}


final class InstantExecutionWarningsSpec extends InstantExecutionProblemSpec {

    private final List<String> uniqueProblems = []

    @Nullable
    private Integer totalProblemsCount

    @Nullable
    private Integer problemsWithStackTraceCount

    InstantExecutionWarningsSpec withUniqueProblems(String... uniqueProblems) {
        return withUniqueProblems(uniqueProblems as List)
    }

    InstantExecutionWarningsSpec withUniqueProblems(Iterable<String> uniqueProblems) {
        this.uniqueProblems.clear()
        this.uniqueProblems.addAll(uniqueProblems)
        return this
    }

    InstantExecutionWarningsSpec withTotalProblemsCount(int totalProblemsCount) {
        this.totalProblemsCount = totalProblemsCount
        return this
    }

    InstantExecutionWarningsSpec withProblemsWithStackTraceCount(int problemsWithStackTraceCount) {
        this.problemsWithStackTraceCount = problemsWithStackTraceCount
        return this
    }

    @PackageScope
    void assertOn(ExecutionResult result, File rootDir) {
        def total = totalProblemsCount ?: uniqueProblems.size()
        validateExpectedProblems(total, uniqueProblems)

        assertProblemsConsoleSummary(result.output, total, uniqueProblems)
        assertProblemsHtmlReport(rootDir, result.output, total, uniqueProblems.size(), problemsWithStackTraceCount)
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
        def problems = uniqueProblems.collect { "> $it".toString() }
        def found = 0
        output.readLines().eachWithIndex { String line, int idx ->
            if (problems.remove(line.trim())) {
                found++
                return
            }
        }
        assert problems.empty, "Expected ${uniqueProblemsCount} unique problems, found ${found} unique problems, remaining:\n${problems.collect { " - $it" }.join("\n")}"
    }
}


final class InstantExecutionFailureSpec extends InstantExecutionProblemSpec {

    private final Class<? extends InstantExecutionException> exceptionType

    private final List<String> uniqueProblems = []

    @Nullable
    private String rootCauseDescription

    @Nullable
    private Integer totalProblemsCount

    @Nullable
    private Integer problemsWithStackTraceCount

    @Nullable
    private String locationFilename

    @Nullable
    private Integer locationLineNumber

    @PackageScope
    InstantExecutionFailureSpec(Class<? extends InstantExecutionException> exceptionType) {
        this.exceptionType = exceptionType
    }

    InstantExecutionFailureSpec withUniqueProblems(String... uniqueProblems) {
        return withUniqueProblems(uniqueProblems as List)
    }

    InstantExecutionFailureSpec withUniqueProblems(Iterable<String> uniqueProblems) {
        this.uniqueProblems.clear()
        this.uniqueProblems.addAll(uniqueProblems)
        return this
    }

    InstantExecutionFailureSpec withRootCauseDescription(String rootCauseDescription) {
        this.rootCauseDescription = rootCauseDescription
        return this
    }

    InstantExecutionFailureSpec withTotalProblemsCount(int totalProblemsCount) {
        this.totalProblemsCount = totalProblemsCount
        return this
    }

    InstantExecutionFailureSpec withProblemsWithStackTraceCount(int problemsWithStackTraceCount) {
        this.problemsWithStackTraceCount = problemsWithStackTraceCount
        return this
    }

    InstantExecutionFailureSpec withLocation(String filename, int lineNumber) {
        locationFilename = filename
        locationLineNumber = lineNumber
        return this
    }

    @PackageScope
    void assertOn(ExecutionResult result, File rootDir) {
        def total = totalProblemsCount ?: uniqueProblems.size()
        validateExpectedProblems(total, uniqueProblems)

        assert result instanceof ExecutionFailure

        def exceptionMessagePrefix = exceptionType.getField("MESSAGE").get(null).toString()
        def summaryHeader = problemsSummaryHeaderFor(total, uniqueProblems.size())

        // No console log summary in stdout
        assertThat(result.output, not(containsString("instant execution problem")))

        // Build failure details problems
        def failureMatcher = allOf(
            startsWith(exceptionMessagePrefix),
            containsString(summaryHeader),
            matchesRegexp(".*See the complete report at file:.*${PROBLEMS_REPORT_HTML_FILE_NAME}")
        )
        if (rootCauseDescription) {
            result.assertHasDescription(rootCauseDescription)
            result.assertThatCause(failureMatcher)
        } else {
            result.assertThatDescription(failureMatcher)
        }
        uniqueProblems.each { problem ->
            result.assertHasCause(problem)
        }

        // Stacktrace contains problems
        assertThat(result.error, containsNormalizedString(
            "${exceptionType.name}: $exceptionMessagePrefix\n$summaryHeader\nSee the complete report at"
        ))
        def detailExceptionType = exceptionType == InstantExecutionErrorsException
            ? InstantExecutionErrorException
            : InstantExecutionProblemException
        if (total == 1) {
            assertThat(result.error, containsString("Caused by: ${detailExceptionType.name}: ${uniqueProblems.first()}"))
        } else {
            (1..total).each { problemNumber ->
                assertThat(result.error, containsString("Cause $problemNumber: ${detailExceptionType.name}:"))
            }
        }

        assertProblemsHtmlReport(rootDir, result.error, total, uniqueProblems.size(), problemsWithStackTraceCount)
        assertLocationOn(result)
    }

    private void assertLocationOn(ExecutionFailure failure) {
        if (locationFilename != null) {
            failure.assertHasFileName(locationFilename)
        }
        if (locationLineNumber != null) {
            failure.assertHasLineNumber(locationLineNumber)
        }
    }
}


@PackageScope
abstract class InstantExecutionProblemSpec {

    protected static final String PROBLEMS_REPORT_HTML_FILE_NAME = "instant-execution-report.html"

    protected static void validateExpectedProblems(int totalProblemsCount, List<String>... uniqueProblems) {
        if (uniqueProblems.length == 0) {
            throw new IllegalArgumentException("Use expectNoInstantExecutionProblem() when expecting no reported problems") // TODO
        }
        if (totalProblemsCount < uniqueProblems.length) {
            throw new IllegalArgumentException("`totalProblemsCount` can't be lesser than `uniqueProblems.length`")
        }
    }

    protected static String problemsSummaryHeaderFor(int totalProblems, int uniqueProblems) {
        return "${totalProblems} instant execution problem${totalProblems >= 2 ? 's were' : ' was'} found, " +
            "${uniqueProblems} of which seem${uniqueProblems >= 2 ? '' : 's'} unique."
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
