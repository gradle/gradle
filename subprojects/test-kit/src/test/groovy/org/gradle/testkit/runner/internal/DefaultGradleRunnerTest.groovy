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
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.gradle.util.SetSystemProperties
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class DefaultGradleRunnerTest extends Specification {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()
    File gradleHome = Mock(File)
    GradleExecutor gradleExecutor = Mock(GradleExecutor)
    TestKitDirProvider testKitDirProvider = Mock(TestKitDirProvider)
    File workingDir = new File('my/tests')
    List<String> arguments = ['compile', 'test', '--parallel', '-Pfoo=bar']

    def "provides expected field values"() {
        when:
        DefaultGradleRunner defaultGradleRunner = createRunner()
        defaultGradleRunner.withProjectDir(workingDir).withArguments(arguments)

        then:
        defaultGradleRunner.projectDir == workingDir
        defaultGradleRunner.arguments == arguments
        defaultGradleRunner.classpath == []
        !defaultGradleRunner.debug
        0 * testKitDirProvider.getDir()
    }

    def "can set custom test kit directory"() {
        given:
        File testKitDir = new File('some/dir')

        when:
        DefaultGradleRunner runner = createRunner()
            .withProjectDir(workingDir)
            .withTestKitDir(testKitDir)

        then:
        runner.projectDir == workingDir
        0 * testKitDirProvider.getDir()
        runner.testKitDirProvider.dir.is testKitDir
    }

    def "throws exception if test kit dir is not writable"() {
        when:
        createRunner().withProjectDir(workingDir).build()

        then:
        1 * testKitDirProvider.getDir() >> {
            Mock(File) {
                isDirectory() >> true
                canWrite() >> false
                getAbsolutePath() >> "path"
            }
        }
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Unable to write to test kit directory: path'
    }

    def "throws exception if test kit exists and is not dir"() {
        when:
        createRunner().withProjectDir(workingDir).build()

        then:
        1 * testKitDirProvider.getDir() >> {
            Mock(File) {
                isDirectory() >> false
                exists() >> true
                getAbsolutePath() >> "path"
            }
        }
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Unable to use non-directory as test kit directory: path'
    }

    def "throws exception if test kit dir cannot be created"() {
        when:
        createRunner().withProjectDir(workingDir).build()

        then:
        1 * testKitDirProvider.getDir() >> {
            Mock(File) {
                isDirectory() >> false
                exists() >> false
                mkdirs() >> false
                getAbsolutePath() >> "path"
            }
        }
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Unable to create test kit directory: path'
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

    def "temporary working space directory is not created if Gradle user home directory is not provided by user when build is requested"() {
        given:
        File gradleUserHomeDir = new File('some/dir')

        when:
        DefaultGradleRunner defaultGradleRunner = createRunnerWithWorkingDirAndArgument()
        defaultGradleRunner.build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run(gradleHome, gradleUserHomeDir, workingDir, arguments, [], [], false) >> new GradleExecutionResult(new ByteArrayOutputStream(), new ByteArrayOutputStream(), null)
    }

    def "temporary working space directory is not created if Gradle user home directory is not provided by user when build and fail is requested"() {
        given:
        File gradleUserHomeDir = new File('some/dir')

        when:
        DefaultGradleRunner defaultGradleRunner = createRunnerWithWorkingDirAndArgument()
        defaultGradleRunner.build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run(gradleHome, gradleUserHomeDir, workingDir, arguments, [], [], false) >> new GradleExecutionResult(new ByteArrayOutputStream(), new ByteArrayOutputStream(), null)
    }

    def "debug flag is passed on to executor"() {
        given:
        File gradleUserHomeDir = new File('some/dir')

        when:
        DefaultGradleRunner defaultGradleRunner = createRunnerWithWorkingDirAndArgument().withDebug(debug)
        defaultGradleRunner.build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run(gradleHome, gradleUserHomeDir, workingDir, arguments, [], [], debug) >> new GradleExecutionResult(new ByteArrayOutputStream(), new ByteArrayOutputStream(), null)

        where:
        debug << [true, false]
    }

    @Unroll
    def "debug flag is #description for system property value '#systemPropertyValue'"() {
        given:
        System.properties[GradleRunner.DEBUG_SYS_PROP] = systemPropertyValue

        when:
        DefaultGradleRunner defaultGradleRunner = createRunner()

        then:
        defaultGradleRunner.debug == debugEnabled

        where:
        systemPropertyValue | debugEnabled | description
        "true"              | true         | 'enabled'
        "false"             | false        | 'disabled'
        "test"              | false        | 'disabled'
    }

    private DefaultGradleRunner createRunner() {
        new DefaultGradleRunner(gradleHome, gradleExecutor, testKitDirProvider)
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
