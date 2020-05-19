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
import junit.framework.AssertionFailedError
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheMaxProblemsOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.LogContent
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.ConfigureUtil
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

import javax.annotation.Nullable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import java.nio.file.Paths
import java.util.regex.Pattern

import static org.gradle.util.Matchers.normalizedLineSeparators
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

final class InstantExecutionProblemsFixture {

    static final String STRICT_CLI_OPTION = "--${ConfigurationCacheOption.LONG_OPTION}=on"
    static final String LENIENT_CLI_OPTION = "--${ConfigurationCacheOption.LONG_OPTION}=warn"
    static final String MAX_PROBLEMS_CLI_OPTION = "-D${ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}"

    protected static final String PROBLEMS_REPORT_HTML_FILE_NAME = "configuration-cache-report.html"

    private final GradleExecuter executer
    private final File rootDir

    InstantExecutionProblemsFixture(GradleExecuter executer, File rootDir) {
        this.executer = executer
        this.rootDir = rootDir
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
            assertHasConsoleSummary(failure.output, spec)
            assertProblemsHtmlReport(failure.output, rootDir, spec)
        } else {
            assertNoProblemsSummary(failure.output)
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
        assertNoProblemsSummary(failure.output)
        assertFailureDescription(failure, spec.rootCauseDescription, failureDescriptionMatcherForProblems(spec))
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
        assertNoProblemsSummary(failure.output)
        assertFailureDescription(failure, spec.rootCauseDescription, failureDescriptionMatcherForTooManyProblems(spec))
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
        if (spec.hasProblems()) {
            assertHasConsoleSummary(result.output, spec)
            assertProblemsHtmlReport(result.output, rootDir, spec)
        } else {
            assertNoProblemsSummary(result.output)
        }
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
        return equalTo("Configuration cache state could not be cached: ${spec.error}".toString())
    }

    private static Matcher<String> failureDescriptionMatcherForProblems(HasInstantExecutionProblemsSpec spec) {
        return buildMatcherForProblemsFailureDescription(
            "Configuration cache problems found in this build.\n" +
                "Gradle can be made to ignore these problems, " +
                "see ${new DocumentationRegistry().getDocumentationFor("configuration_cache", "ignore_problems")}.",
            spec
        )
    }

    private static Matcher<String> failureDescriptionMatcherForTooManyProblems(HasInstantExecutionProblemsSpec spec) {
        return buildMatcherForProblemsFailureDescription(
            "Maximum number of configuration cache problems has been reached.\n" +
                "This behavior can be adjusted, " +
                "see ${new DocumentationRegistry().getDocumentationFor("configuration_cache", "max_problems")}",
            spec
        )
    }

    private static Matcher<String> buildMatcherForProblemsFailureDescription(
        String message,
        HasInstantExecutionProblemsSpec spec
    ) {
        return new BaseMatcher<String>() {
            @Override
            boolean matches(Object item) {
                assert item.toString().contains(message)
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

    private static void assertHasConsoleSummary(String text, HasInstantExecutionProblemsSpec spec) {
        def uniqueCount = spec.uniqueProblems.size()
        def totalCount = spec.totalProblemsCount ?: uniqueCount

        def summary = extractSummary(text)
        assert summary.totalProblems == totalCount
        assert summary.uniqueProblems == uniqueCount
        assert summary.messages == spec.uniqueProblems
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
            def jsFile = reportDir.file('configuration-cache-report-data.js')
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
        def baseDirUri = clickableUrlFor(new File(rootDir, "build/reports/configuration-cache"))
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
            eval("configurationCacheProblems().problems.length") as int
        }
    }

    protected static int numberOfProblemsWithStacktraceIn(File jsFile) {
        newJavaScriptEngine().with {
            eval(jsFile.text)
            eval("configurationCacheProblems().problems.filter(function(problem) { return problem['error'] != null; }).length") as int
        }
    }

    private static ScriptEngine newJavaScriptEngine() {
        new ScriptEngineManager().getEngineByName("JavaScript")
    }

    protected static Matcher<String> containsNormalizedString(String string) {
        return normalizedLineSeparators(containsString(string))
    }

    private static ProblemsSummary extractSummary(String text) {
        def headerPattern = Pattern.compile("(\\d+) configuration cache (problems were|problem was) found(, (\\d+) of which seem(s)? unique)?.*")
        def problemPattern = Pattern.compile("- (.*)")
        def docPattern = Pattern.compile(" {2}\\QSee https://docs.gradle.org\\E.*")

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
        def uniqueProblems = matcher.group(4)?.toInteger() ?: totalProblems
        summary = summary.drop(1)

        def problems = []
        for (int i = 0; i < uniqueProblems; i++) {
            matcher = problemPattern.matcher(summary.first)
            if (!matcher.matches()) {
                throw new AssertionFailedError("""Expected a problem description, found: ${summary.first}""")
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

        return new ProblemsSummary(totalProblems, uniqueProblems, problems)
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

    HasInstantExecutionProblemsSpec withProblem(String problem) {
        uniqueProblems.add(problem)
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

