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
        GradleException exception = new GradleException("<message>");

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def "does not suggest to use --scan if option was on command line"() {
        GradleException exception = new GradleException("<message>");

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
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def "does not suggest to use --scan if --no-scan is on command line"() {
        GradleException exception = new GradleException("<message>");

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
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.

* Get more help at {userinput}https://help.gradle.org{normal}
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
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def reportsLocationAwareException() {
        Throwable exception = new LocationAwareException(new RuntimeException("<message>", new RuntimeException("<cause>")), "<location>", 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location> line: 42

* What went wrong:
<message>
{info}> {normal}<cause>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def reportsLocationAwareExceptionWithNoMessage() {
        Throwable exception = new LocationAwareException(new RuntimeException(new IOException()), "<location>", 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location> line: 42

* What went wrong:
java.io.IOException
{info}> {normal}java.io.IOException (no error message)

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def reportsLocationAwareExceptionWithMultipleCauses() {
        Throwable exception = new LocationAwareException(new DefaultMultiCauseException("<message>", new RuntimeException("<cause1>"), new RuntimeException("<cause2>")), "<location>", 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location> line: 42

* What went wrong:
<message>
{info}> {normal}<cause1>
{info}> {normal}<cause2>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def reportsLocationAwareExceptionWithMultipleNestedCauses() {
        def cause1 = new DefaultMultiCauseException("<cause1>", new RuntimeException("<cause1.1>"), new RuntimeException("<cause1.2>"))
        def cause2 = new DefaultMultiCauseException("<cause2>", new RuntimeException("<cause2.1>"))
        Throwable exception = new LocationAwareException(new DefaultMultiCauseException("<message>", cause1, cause2), "<location>", 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location> line: 42

* What went wrong:
<message>
{info}> {normal}<cause1>
   {info}> {normal}<cause1.1>
   {info}> {normal}<cause1.2>
{info}> {normal}<cause2>
   {info}> {normal}<cause2.1>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def reportsLocationAwareExceptionWhenCauseHasNoMessage() {
        Throwable exception = new LocationAwareException(new RuntimeException("<message>", new RuntimeException()), "<location>", 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location> line: 42

* What went wrong:
<message>
{info}> {normal}java.lang.RuntimeException (no error message)

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def showsStacktraceOfCauseOfLocationAwareException() {
        configuration.showStacktrace = ShowStacktrace.ALWAYS

        Throwable exception = new LocationAwareException(new GradleException("<message>", new GradleException('<failure>')), "<location>", 42)

        expect:
        reporter.buildFinished(result(exception))
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* Where:
<location> line: 42

* What went wrong:
<message>
{info}> {normal}<failure>

* Try:
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Exception is:
org.gradle.api.GradleException: <message>
{stacktrace}
Caused by: org.gradle.api.GradleException: <failure>
{stacktrace}
* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def reportsMultipleBuildFailures() {
        def failure1 = new LocationAwareException(new RuntimeException("<message>", new RuntimeException("<cause>")), "<location>", 42)
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
<location> line: 42

* What went wrong:
<message>
{info}> {normal}<cause>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.
==============================================================================

{failure}2: {normal}{failure}Task failed with an exception.{normal}
-----------
* What went wrong:
<failure>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.
==============================================================================

{failure}3: {normal}{failure}Task failed with an exception.{normal}
-----------
* What went wrong:
<error>

* Try:
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.
==============================================================================

* Get more help at {userinput}https://help.gradle.org{normal}
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
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Exception is:
org.gradle.api.GradleException: <message>
{stacktrace}
* Get more help at {userinput}https://help.gradle.org{normal}
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
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Exception is:
org.gradle.api.GradleException: <message>
{stacktrace}
* Get more help at {userinput}https://help.gradle.org{normal}
'''
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
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
{info}> {normal}resolution 1.
{info}> {normal}resolution 2.
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.
{info}> {normal}Run with {userinput}--scan{normal} to get full insights.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
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
        output.value == '''
{failure}FAILURE: {normal}{failure}Build failed with an exception.{normal}

* What went wrong:
<message>

* Try:
{info}> {normal}resolution 1.
{info}> {normal}Run with {userinput}--stacktrace{normal} option to get the stack trace.
{info}> {normal}Run with {userinput}--info{normal} or {userinput}--debug{normal} option to get more log output.

* Get more help at {userinput}https://help.gradle.org{normal}
'''
    }

    def result(Throwable failure) {
        BuildResult result = Mock()
        result.failure >> failure
        result
    }

    abstract class TestException extends GradleException implements FailureResolutionAware {
        TestException() {
            super("<message>")
        }
    }
}
