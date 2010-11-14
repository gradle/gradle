/*
 * Copyright 2010 the original author or authors.
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
package org.gradle

import org.gradle.StartParameter.ShowStacktrace
import org.gradle.api.GradleException
import org.gradle.api.LocationAwareException
import org.gradle.api.logging.LogLevel
import org.gradle.execution.TaskSelectionException
import org.gradle.initialization.BuildClientMetaData
import org.gradle.logging.StyledTextOutputFactory
import org.gradle.logging.internal.TestStyledTextOutput
import spock.lang.Specification

class BuildExceptionReporterTest extends Specification {
    final TestStyledTextOutput output = new TestStyledTextOutput()
    final StyledTextOutputFactory factory = Mock()
    final BuildClientMetaData clientMetaData = Mock()
    final StartParameter startParameter = new StartParameter()
    final BuildExceptionReporter reporter = new BuildExceptionReporter(factory, startParameter, clientMetaData)

    def setup() {
        _ * factory.create(BuildExceptionReporter.class, LogLevel.ERROR) >> output
        _ * clientMetaData.describeCommand(!null, !null) >> { args -> args[0].append("[gradle ${args[1].join(' ')}]")}
    }

    def doesNothingWheBuildIsSuccessful() {
        expect:
        reporter.buildFinished(result(null))
        output.value == ''
    }

    def reportsInternalFailure() {
        final RuntimeException exception = new RuntimeException("<message>");

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build aborted because of an internal error.{normal}

* What went wrong:
Build aborted because of an unexpected internal error. Please file an issue at: http://www.gradle.org.

* Try:
Run with {userinput}-d{normal} option to get additional debug info.

* Exception is:
java.lang.RuntimeException: <message>
{stacktrace}
'''
    }

    def reportsBuildFailure() {
        GradleException exception = new GradleException("<message>");

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
Run with {userinput}-s{normal} or {userinput}-d{normal} option to get more details. Run with {userinput}-S{normal} option to get the full (very verbose) stacktrace.
'''
    }

    def reportsBuildFailureWhenFailureHasNoMessage() {
        GradleException exception = new GradleException();

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
org.gradle.api.GradleException (no error message)

* Try:
Run with {userinput}-s{normal} or {userinput}-d{normal} option to get more details. Run with {userinput}-S{normal} option to get the full (very verbose) stacktrace.
'''
    }

    def reportsLocationAwareException() {
        Throwable exception = exception("<location>", "<message>", new RuntimeException("<cause>"));

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location>

* What went wrong:
<message>
Cause: <cause>

* Try:
Run with {userinput}-s{normal} or {userinput}-d{normal} option to get more details. Run with {userinput}-S{normal} option to get the full (very verbose) stacktrace.
'''
    }

    def reportsLocationAwareExceptionWithMultipleCauses() {
        Throwable exception = exception("<location>", "<message>", new RuntimeException("<outer>"), new RuntimeException("<cause>"));

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location>

* What went wrong:
<message>
Cause: <outer>
Cause: <cause>

* Try:
Run with {userinput}-s{normal} or {userinput}-d{normal} option to get more details. Run with {userinput}-S{normal} option to get the full (very verbose) stacktrace.
'''
    }

    def reportsLocationAwareExceptionWhenCauseHasNoMessage() {
        Throwable exception = exception("<location>", "<message>", new RuntimeException());

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location>

* What went wrong:
<message>
Cause: java.lang.RuntimeException (no error message)

* Try:
Run with {userinput}-s{normal} or {userinput}-d{normal} option to get more details. Run with {userinput}-S{normal} option to get the full (very verbose) stacktrace.
'''
    }

    def reportsTaskSelectionException() {
        Throwable exception = new TaskSelectionException("<message>");

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Could not determine which tasks to execute.{normal}

* What went wrong:
<message>

* Try:
Run {userinput}[gradle tasks]{normal} to get a list of available tasks.
'''
    }

    def reportsBuildFailureWhenShowStacktraceEnabled() {
        startParameter.showStacktrace = ShowStacktrace.ALWAYS

        GradleException exception = new GradleException('<message>')

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
Run with {userinput}-d{normal} option to get more details. Run with {userinput}-S{normal} option to get the full (very verbose) stacktrace.

* Exception is:
org.gradle.api.GradleException: <message>
{stacktrace}
'''
    }

    def reportsBuildFailureWhenShowFullStacktraceEnabled() {
        startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL

        GradleException exception = new GradleException('<message>')

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
Run with {userinput}-d{normal} option to get more details. 

* Exception is:
org.gradle.api.GradleException: <message>
{stacktrace}
'''
    }

    def reportsBuildFailureWhenDebugLoggingEnabled() {
        startParameter.logLevel = LogLevel.DEBUG

        GradleException exception = new GradleException('<message>')

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Exception is:
org.gradle.api.GradleException: <message>
{stacktrace}
'''
    }

    def result(Throwable failure) {
        BuildResult result = Mock()
        _ * result.failure >> failure
        result
    }

    def exception(final String location, final String message, final Throwable... causes) {
        TestException exception = Mock()
        _ * exception.location >> location
        _ * exception.originalMessage >> message
        _ * exception.reportableCauses >> (causes as List)
        exception
    }
}

public abstract class TestException extends GradleException implements LocationAwareException {
}
