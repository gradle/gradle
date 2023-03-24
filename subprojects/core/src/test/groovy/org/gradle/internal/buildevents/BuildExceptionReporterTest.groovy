/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.buildevents

import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.GradleException
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.execution.MultipleBuildFailures
import org.gradle.initialization.BuildClientMetaData
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.exceptions.FailureResolutionAware
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.logging.DefaultLoggingConfiguration
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutput
import spock.lang.Specification

class BuildExceptionReporterTest extends Specification {
    final TestStyledTextOutput output = new TestStyledTextOutput()
    final StyledTextOutputFactory factory = Mock()
    final BuildClientMetaData clientMetaData = Mock()
    final GradleEnterprisePluginManager gradleEnterprisePluginManager = Mock()
    final LoggingConfiguration configuration = new DefaultLoggingConfiguration()
    final BuildExceptionReporter reporter = new BuildExceptionReporter(factory, configuration, clientMetaData, gradleEnterprisePluginManager)


    static final String MESSAGE = "<message>"
    static final String FAILURE = '<failure>'
    static final String LOCATION = "<location>"
    static final String STACKTRACE = "{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace."
    static final String INFO_OR_DEBUG = "{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output."
    static final String INFO = "{info}> {normal}Run with {userinput}--info{normal} option to get more log output."
    static final String SCAN = "{info}> {normal}Run with {userinput}--scan{normal} to get full insights."
    static final String GET_HELP = "{info}> {normal}Get more help at {userinput}https://help.gradle.org{normal}"


    def setup() {
        factory.create(BuildExceptionReporter.class, LogLevel.ERROR) >> output
        clientMetaData.describeCommand(!null, !null) >> { args -> args[0].append("[gradle ${args[1].join(' ')}]") }
    }

    def doesNothingWhenBuildIsSuccessful() {
        expect:
        reporter.buildFinished(result(null))
        output.value == ''
    }

    def reportsBuildFailure() {
        GradleException exception = new GradleException(MESSAGE);

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
$MESSAGE

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
"""
    }

    def "does not suggest to use --scan if option was on command line"() {
        GradleException exception = new GradleException(MESSAGE);

        def result = result(exception)
        result.gradle >> Mock(Gradle) {
            getStartParameter() >> Mock(StartParameter) {
                isBuildScan() >> true
                isNoBuildScan() >> false
            }
        }
        gradleEnterprisePluginManager.isPresent() >> true

        expect:
        reporter.buildFinished(result)
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
$MESSAGE

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$GET_HELP
"""
    }

    def "does not suggest to use --scan if --no-scan is on command line"() {
        GradleException exception = new GradleException(MESSAGE);

        def result = result(exception)
        result.gradle >> Mock(Gradle) {
            getStartParameter() >> Mock(StartParameter) {
                isBuildScan() >> false
                isNoBuildScan() >> true
            }
        }
        gradleEnterprisePluginManager.isPresent() >> true

        expect:
        reporter.buildFinished(result)
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
$MESSAGE

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$GET_HELP
"""
    }

    def reportsBuildFailureWhenFailureHasNoMessage() {
        GradleException exception = new GradleException();

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
org.gradle.api.GradleException (no error message)

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
"""
    }

    def reportsLocationAwareException() {
        Throwable exception = new LocationAwareException(new RuntimeException(MESSAGE, new RuntimeException("<cause>")), LOCATION, 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
$LOCATION line: 42

* What went wrong:
$MESSAGE
{info}> {normal}<cause>

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
"""
    }

    def reportsLocationAwareExceptionWithNoMessage() {
        Throwable exception = new LocationAwareException(new RuntimeException(new IOException()), LOCATION, 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
$LOCATION line: 42

* What went wrong:
java.io.IOException
{info}> {normal}java.io.IOException (no error message)

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
"""
    }

    def reportsLocationAwareExceptionWithMultipleCauses() {
        Throwable exception = new LocationAwareException(new DefaultMultiCauseException(MESSAGE, new RuntimeException("<cause1>"), new RuntimeException("<cause2>")), LOCATION, 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
$LOCATION line: 42

* What went wrong:
$MESSAGE
{info}> {normal}<cause1>
{info}> {normal}<cause2>

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
"""
    }

    def reportsLocationAwareExceptionWithMultipleNestedCauses() {
        def cause1 = new DefaultMultiCauseException("<cause1>", new RuntimeException("<cause1.1>"), new RuntimeException("<cause1.2>"))
        def cause2 = new DefaultMultiCauseException("<cause2>", new RuntimeException("<cause2.1>"))
        Throwable exception = new LocationAwareException(new DefaultMultiCauseException(MESSAGE, cause1, cause2), LOCATION, 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
$LOCATION line: 42

* What went wrong:
$MESSAGE
{info}> {normal}<cause1>
   {info}> {normal}<cause1.1>
   {info}> {normal}<cause1.2>
{info}> {normal}<cause2>
   {info}> {normal}<cause2.1>

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
"""
    }

    def reportsLocationAwareExceptionWhenCauseHasNoMessage() {
        Throwable exception = new LocationAwareException(new RuntimeException(MESSAGE, new RuntimeException()), LOCATION, 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
$LOCATION line: 42

* What went wrong:
$MESSAGE
{info}> {normal}java.lang.RuntimeException (no error message)

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
"""
    }

    def showsStacktraceOfCauseOfLocationAwareException() {
        configuration.showStacktrace = ShowStacktrace.ALWAYS

        Throwable exception = new LocationAwareException(new GradleException(MESSAGE, new GradleException(FAILURE)), LOCATION, 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
$LOCATION line: 42

* What went wrong:
$MESSAGE
{info}> {normal}$FAILURE

* Try:
$INFO_OR_DEBUG
$SCAN
$GET_HELP

* Exception is:
org.gradle.api.GradleException: $MESSAGE
{stacktrace}
Caused by: org.gradle.api.GradleException: $FAILURE
{stacktrace}
"""
    }

    def "report multiple failures and skip help link for NonGradleCauseException"() {
        def failure1 = new LocationAwareException(new TaskExecutionException(null, new TestNonGradleCauseException()), LOCATION, 42)
        def failure2 = new LocationAwareException(new TaskExecutionException(null, new TestCompilationFailureException()), LOCATION, 42)
        def failure3 = new RuntimeException("<error>")
        Throwable exception = new MultipleBuildFailures([failure1, failure2, failure3])

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: Build completed with 3 failures.{normal}

{failure}1: {normal}{failure}Task failed with an exception.{normal}
-----------
* Where:
$LOCATION line: 42

* What went wrong:
Execution failed for null.
{info}> {normal}org.gradle.internal.buildevents.TestNonGradleCauseException (no error message)

* Try:
$SCAN
==============================================================================

{failure}2: {normal}{failure}Task failed with an exception.{normal}
-----------
* Where:
$LOCATION line: 42

* What went wrong:
Execution failed for null.
{info}> {normal}org.gradle.internal.buildevents.TestCompilationFailureException (no error message)

* Try:
$INFO
$SCAN
==============================================================================

{failure}3: {normal}{failure}Task failed with an exception.{normal}
-----------
* What went wrong:
<error>

* Try:
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
==============================================================================
""";
    }

    def reportsBuildFailureWhenShowStacktraceEnabled() {
        configuration.showStacktrace = ShowStacktrace.ALWAYS

        GradleException exception = new GradleException(MESSAGE)

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
$MESSAGE

* Try:
$INFO_OR_DEBUG
$SCAN
$GET_HELP

* Exception is:
org.gradle.api.GradleException: $MESSAGE
{stacktrace}
"""
    }

    def reportsBuildFailureWhenShowFullStacktraceEnabled() {
        configuration.showStacktrace = ShowStacktrace.ALWAYS_FULL

        GradleException exception = new GradleException(MESSAGE)

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
$MESSAGE

* Try:
$INFO_OR_DEBUG
$SCAN
$GET_HELP

* Exception is:
org.gradle.api.GradleException: $MESSAGE
{stacktrace}
"""
    }

    def includesResolutionsFromExceptionWhenItImplementsFailureResolutionAware() {
        def exception = new TestException() {
            @Override
            void appendResolutions(FailureResolutionAware.Context context) {
                context.appendResolution { output -> output.append("resolution 1.")}
                context.appendResolution { output -> output.append("resolution 2.")}
            }
        }

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
$MESSAGE

* Try:
{info}> {normal}resolution 1.
{info}> {normal}resolution 2.
$STACKTRACE
$INFO_OR_DEBUG
$SCAN
$GET_HELP
"""
    }

    def doesNotSuggestGeneratingAScanWhenTheBuildIsMissing() {
        def exception = new TestException() {
            @Override
            void appendResolutions(FailureResolutionAware.Context context) {
                context.doNotSuggestResolutionsThatRequireBuildDefinition()
                context.appendResolution { output -> output.append("resolution 1.")}
            }
        }

        expect:
        reporter.buildFinished(result(exception))
        output.value == """
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
$MESSAGE

* Try:
{info}> {normal}resolution 1.
$STACKTRACE
$INFO_OR_DEBUG
$GET_HELP
"""
    }

    def result(Throwable failure) {
        BuildResult result = Mock()
        result.failure >> failure
        result
    }

    abstract class TestException extends GradleException implements FailureResolutionAware {
        TestException() {
            super(MESSAGE)
        }
    }
}
