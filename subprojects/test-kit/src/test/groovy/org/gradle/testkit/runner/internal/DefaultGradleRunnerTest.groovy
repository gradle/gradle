/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal

import org.gradle.api.GradleException
import org.gradle.api.UncheckedIOException
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Unroll

class DefaultGradleRunnerTest extends Specification {
    File gradleHome = Mock(File)
    GradleRunnerWorkingSpaceDirectoryProvider gradleRunnerWorkingSpaceDirectoryProvider = Mock(GradleRunnerWorkingSpaceDirectoryProvider)
    File workingDir = new File('my/tests')
    List<String> arguments = ['compile', 'test', '--parallel', '-Pfoo=bar']

    def "provides expected field values"() {
        given:
        File gradleUserHome = new File('some/dir')

        when:
        DefaultGradleRunner defaultGradleRunner = createRunner()
        defaultGradleRunner.withProjectDir(workingDir).withArguments(arguments)

        then:
        defaultGradleRunner.projectDir == workingDir
        defaultGradleRunner.arguments == arguments
        1 * gradleRunnerWorkingSpaceDirectoryProvider.createDir() >> gradleUserHome
        defaultGradleRunner.gradleUserHomeDir == gradleUserHome
        defaultGradleRunner.classpath == []
    }

    def "throws exception if working Gradle user home directory cannot be created"() {
        when:
        createRunner()

        then:
        1 * gradleRunnerWorkingSpaceDirectoryProvider.createDir() >> { throw new UncheckedIOException() }
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Unable to create or write to Gradle user home directory for test execution'
    }

    def "returned arguments are unmodifiable"() {
        when:
        createRunner().arguments << '-i'

        then:
        thrown(UnsupportedOperationException)
    }

    def "returned classpath is unmodifiable"() {
        when:
        createRunner().classpath << new URI('file:///Users/foo/bar/test.jar')

        then:
        thrown(UnsupportedOperationException)
    }

    def "creates defensive copy of passed in argument lists"() {
        given:
        def originalArguments = ['arg1', 'arg2']
        def originalJvmArguments = ['arg3', 'arg4']
        def originalClasspath = [new URI('file:///Users/foo/bar/test.jar')]
        DefaultGradleRunner defaultGradleRunner = createRunner()

        when:
        defaultGradleRunner.withArguments(originalArguments)
        defaultGradleRunner.withJvmArguments(originalJvmArguments)
        defaultGradleRunner.withClasspath(originalClasspath)

        then:
        defaultGradleRunner.arguments == originalArguments
        defaultGradleRunner.jvmArguments == originalJvmArguments
        defaultGradleRunner.classpath == originalClasspath

        when:
        originalArguments << 'arg5'
        originalJvmArguments << 'arg6'
        originalClasspath << new URI('file:///Users/foo/bar/other.jar')

        then:
        defaultGradleRunner.arguments == ['arg1', 'arg2']
        defaultGradleRunner.jvmArguments == ['arg3', 'arg4']
        defaultGradleRunner.classpath == [new URI('file:///Users/foo/bar/test.jar')]
    }

    def "throws exception if working directory is not provided when build is requested"() {
        when:
        DefaultGradleRunner defaultGradleRunner = createRunner()
        defaultGradleRunner.build()

        then:
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Please specify a project directory before executing the build'
    }

    def "throws exception if working directory is not provided when build and fail is requested"() {
        when:
        DefaultGradleRunner defaultGradleRunner = createRunner()
        defaultGradleRunner.buildAndFail()

        then:
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Please specify a project directory before executing the build'
    }

    def "creates diagnostic message for execution result without thrown exception"() {
        given:
        DefaultGradleRunner defaultGradleRunner = createRunnerWithWorkingDirAndArgument()
        GradleExecutionResult gradleExecutionResult = createGradleExecutionResult()

        when:
        String message = defaultGradleRunner.createDiagnosticsMessage('Gradle build executed', gradleExecutionResult)

        then:
        TextUtil.normaliseLineSeparators(message) == basicDiagnosticsMessage
    }

    @Unroll
    def "creates diagnostic message for execution result for thrown #description"() {
        given:
        DefaultGradleRunner defaultGradleRunner = createRunnerWithWorkingDirAndArgument()
        GradleExecutionResult gradleExecutionResult = createGradleExecutionResult(exception)

        when:
        String message = defaultGradleRunner.createDiagnosticsMessage('Gradle build executed', gradleExecutionResult)

        then:
        TextUtil.normaliseLineSeparators(message) == """$basicDiagnosticsMessage
Reason:
$expectedReason
-----"""

        where:
        exception                                                                                                                     | expectedReason                | description
        new RuntimeException('Something went wrong')                                                                                  | 'Something went wrong'        | 'exception having no parent cause'
        new RuntimeException('Something went wrong', new GradleException('Unknown command line option'))                              | 'Unknown command line option' | 'exception having single parent cause'
        new RuntimeException('Something went wrong', new GradleException('Unknown command line option', new Exception('Total fail'))) | 'Total fail'                  | 'exception having multiple parent causes'
    }

    private DefaultGradleRunner createRunner() {
        new DefaultGradleRunner(gradleHome, gradleRunnerWorkingSpaceDirectoryProvider)
    }

    private DefaultGradleRunner createRunnerWithWorkingDirAndArgument() {
        createRunner().withProjectDir(workingDir).withArguments(arguments)
    }

    private GradleExecutionResult createGradleExecutionResult(Throwable throwable = null) {
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream()
        standardOutput.write('This is some output'.bytes)
        ByteArrayOutputStream standardError = new ByteArrayOutputStream()
        standardError.write('This is some error'.bytes)
        List<BuildResult> tasks = new ArrayList<BuildResult>();
        new GradleExecutionResult(standardOutput, standardError, tasks, throwable)
    }

    private String getBasicDiagnosticsMessage() {
        """Gradle build executed in $workingDir.absolutePath with arguments $arguments

Output:
This is some output
$DefaultGradleRunner.DIAGNOSTICS_MESSAGE_SEPARATOR
Error:
This is some error
$DefaultGradleRunner.DIAGNOSTICS_MESSAGE_SEPARATOR"""
    }
}
