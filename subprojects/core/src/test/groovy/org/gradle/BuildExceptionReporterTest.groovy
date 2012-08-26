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

import org.gradle.api.GradleException
import org.gradle.api.internal.LocationAwareException
import org.gradle.api.logging.LogLevel
import org.gradle.api.internal.AbstractMultiCauseException
import org.gradle.execution.TaskSelectionException
import org.gradle.initialization.BuildClientMetaData
import org.gradle.logging.LoggingConfiguration
import org.gradle.logging.ShowStacktrace
import org.gradle.logging.StyledTextOutputFactory
import org.gradle.logging.TestStyledTextOutput
import org.gradle.util.TreeVisitor

import spock.lang.Specification
import org.gradle.execution.MultipleBuildFailures

class BuildExceptionReporterTest extends Specification {
    final TestStyledTextOutput output = new TestStyledTextOutput()
    final StyledTextOutputFactory factory = Mock()
    final BuildClientMetaData clientMetaData = Mock()
    final LoggingConfiguration configuration = new LoggingConfiguration()
    final BuildExceptionReporter reporter = new BuildExceptionReporter(factory, configuration, clientMetaData)

    def setup() {
        factory.create(BuildExceptionReporter.class, LogLevel.ERROR) >> output
        clientMetaData.describeCommand(!null, !null) >> { args -> args[0].append("[gradle ${args[1].join(' ')}]")}
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
Build aborted because of an unexpected internal error. Please file an issue at: http://forums.gradle.org.

* Try:
Run with {userinput}--debug{normal} option to get additional debug info.

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
Run with {userinput}--stacktrace{normal} option to get the stack trace. Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
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
Run with {userinput}--stacktrace{normal} option to get the stack trace. Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
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
{info}> {normal}<cause>

* Try:
Run with {userinput}--stacktrace{normal} option to get the stack trace. Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
'''
    }

    def reportsLocationAwareExceptionWithMultipleCauses() {
        Throwable exception = exception("<location>", "<message>", new RuntimeException("<cause1>"), new RuntimeException("<cause2>"));

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location>

* What went wrong:
<message>
{info}> {normal}<cause1>
{info}> {normal}<cause2>

* Try:
Run with {userinput}--stacktrace{normal} option to get the stack trace. Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
'''
    }

    def reportsLocationAwareExceptionWithMultipleNestedCauses() {
        def cause1 = nested("<cause1>", new RuntimeException("<cause1.1>"), new RuntimeException("<cause1.2>"))
        def cause2 = nested("<cause2>", new RuntimeException("<cause2.1>"))
        Throwable exception = exception("<location>", "<message>", cause1, cause2);

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location>

* What went wrong:
<message>
{info}> {normal}<cause1>
   {info}> {normal}<cause1.1>
   {info}> {normal}<cause1.2>
{info}> {normal}<cause2>
   {info}> {normal}<cause2.1>

* Try:
Run with {userinput}--stacktrace{normal} option to get the stack trace. Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
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
{info}> {normal}java.lang.RuntimeException (no error message)

* Try:
Run with {userinput}--stacktrace{normal} option to get the stack trace. Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
'''
    }

    def showsStacktraceOfCauseOfLocationAwareException() {
        configuration.showStacktrace = ShowStacktrace.ALWAYS

        Throwable exception = exception("<location>", "<message>", new GradleException('<failure>'))

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location>

* What went wrong:
<message>
{info}> {normal}<failure>

* Try:
Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.

* Exception is:
org.gradle.api.GradleException: <failure>
{stacktrace}
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

    def reportsMultipleBuildFailures() {
        def failure1 = exception("<location>", "<message>", new RuntimeException("<cause>"))
        def failure2 = new GradleException("<failure>")
        def failure3 = new RuntimeException("<error>")
        Throwable exception = new MultipleBuildFailures([failure1, failure2, failure3])

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: Build completed with 3 failures.{normal}

{failure}1: {normal}{failure}Task failed with an exception.{normal}
-----------
* Where:
<location>

* What went wrong:
<message>
{info}> {normal}<cause>

* Try:
Run with {userinput}--stacktrace{normal} option to get the stack trace. Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
==============================================================================

{failure}2: {normal}{failure}Task failed with an exception.{normal}
-----------
* What went wrong:
<failure>

* Try:
Run with {userinput}--stacktrace{normal} option to get the stack trace. Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
==============================================================================

{failure}3: {normal}{failure}Build aborted because of an internal error.{normal}
-----------
* What went wrong:
Build aborted because of an unexpected internal error. Please file an issue at: http://forums.gradle.org.

* Try:
Run with {userinput}--debug{normal} option to get additional debug info.

* Exception is:
java.lang.RuntimeException: <error>
{stacktrace}
==============================================================================
''';
    }

    def reportsBuildFailureWhenShowStacktraceEnabled() {
        configuration.showStacktrace = ShowStacktrace.ALWAYS

        GradleException exception = new GradleException('<message>')

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.

* Exception is:
org.gradle.api.GradleException: <message>
{stacktrace}
'''
    }

    def reportsBuildFailureWhenShowFullStacktraceEnabled() {
        configuration.showStacktrace = ShowStacktrace.ALWAYS_FULL

        GradleException exception = new GradleException('<message>')

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.

* Exception is:
org.gradle.api.GradleException: <message>
{stacktrace}
'''
    }

    def reportsBuildFailureWhenDebugLoggingEnabled() {
        configuration.logLevel = LogLevel.DEBUG

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
        result.failure >> failure
        result
    }
    
    def nested(String message, Throwable... causes) {
        return new TestException(message, causes)
    }
    
    def exception(String location, String message, Throwable... causes) {
        LocationAwareException exception = Mock()
        exception.location >> location
        exception.originalMessage >> message
        exception.cause >> causes[0]
        exception.visitReportableCauses(!null) >> { TreeVisitor visitor ->
            visitor.node(exception)
            visitor.startChildren()
            causes.each { 
                visitor.node(it) 
                if (it instanceof TestException) {
                    visitor.startChildren()
                    it.causes.each { child ->
                        visitor.node(child)
                    }
                    visitor.endChildren()
                }
            }
            visitor.endChildren()
        }
        exception
    }
}

class TestException extends AbstractMultiCauseException {
    TestException(String message, Throwable... causes) {
        super(message, causes)
    }
}
