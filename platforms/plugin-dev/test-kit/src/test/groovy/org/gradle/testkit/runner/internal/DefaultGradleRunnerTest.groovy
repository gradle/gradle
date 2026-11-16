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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultGradleRunnerTest extends Specification {

    public static final BuildOperationParameters BUILD_OPERATION_PARAMETERS = new BuildOperationParameters(GradleVersion.version('2.4'), false)

    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    GradleExecutor gradleExecutor = Mock(GradleExecutor)
    TestKitDirProvider testKitDirProvider = Mock(TestKitDirProvider)
    File workingDir = new File('my/tests')
    List<String> arguments = ['compile', 'test', '--parallel', '-Pfoo=bar']

    def "provides expected field values"() {
        when:
        def runner = createRunner()
            .withProjectDir(workingDir)
            .withArguments(arguments)

        then:
        runner.projectDir == workingDir
        runner.arguments == arguments
        runner.pluginClasspath == []
        !runner.debug
        !runner.standardOutput
        !runner.standardError
        !runner.standardInput
        0 * testKitDirProvider.getDir()
    }

    def "throws exception if custom test kit directory"() {
        when:
        createRunner().withTestKitDir(null)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'testKitDir argument cannot be null'
    }

    def "can set custom test kit directory"() {
        given:
        def testKitDir = testDirectoryProvider.createDir('some/dir')

        when:
        def runner = createRunner()
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
        createRunner().pluginClasspath << new URI('file:///Users/foo/bar/test.jar')

        then:
        thrown(UnsupportedOperationException)
    }

    def "creates defensive copy of passed in argument lists"() {
        given:
        def originalArguments = ['arg1', 'arg2']
        def originalJvmArguments = ['arg3', 'arg4']
        def originalClasspath = [new File('/Users/foo/bar/test.jar').absoluteFile]

        when:
        def runner = createRunner()
            .withArguments(originalArguments)
            .withJvmArguments(originalJvmArguments)
            .withPluginClasspath(originalClasspath) as DefaultGradleRunner

        then:
        runner.arguments == originalArguments
        runner.jvmArguments == originalJvmArguments
        runner.pluginClasspath == originalClasspath

        when:
        originalArguments << 'arg5'
        originalJvmArguments << 'arg6'
        originalClasspath << new File('file:///Users/foo/bar/other.jar')

        then:
        runner.arguments == ['arg1', 'arg2']
        runner.jvmArguments == ['arg3', 'arg4']
        runner.pluginClasspath == [new File('/Users/foo/bar/test.jar').absoluteFile]
    }

    def "throws exception if working directory is not provided when build is requested"() {
        when:
        createRunner().build()

        then:
        def t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Please specify a project directory before executing the build'
    }

    def "throws exception if working directory is not provided when run is requested"() {
        when:
        createRunner().run()

        then:
        def t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Please specify a project directory before executing the build'
    }

    def "throws exception if working directory is not provided when build and fail is requested"() {
        when:
        createRunner().buildAndFail()

        then:
        def t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Please specify a project directory before executing the build'
    }

    def "creates diagnostic message for execution result without thrown exception"() {
        given:
        def runner = createRunnerWithWorkingDirAndArgument()
        def result = createGradleExecutionResult()

        when:
        def message = runner.createDiagnosticsMessage('Gradle build executed', result)

        then:
        TextUtil.normaliseLineSeparators(message) == basicDiagnosticsMessage
    }

    def "creates diagnostic message for execution result for thrown #description"() {
        given:
        def runner = createRunnerWithWorkingDirAndArgument()
        def result = createGradleExecutionResult(exception)

        when:
        def message = runner.createDiagnosticsMessage('Gradle build executed', result)

        then:
        TextUtil.normaliseLineSeparators(message) == basicDiagnosticsMessage

        where:
        exception                                                                                                                     | expectedReason                | description
        new RuntimeException('Something went wrong')                                                                                  | 'Something went wrong'        | 'exception having no parent cause'
        new RuntimeException('Something went wrong', new GradleException('Unknown command line option'))                              | 'Unknown command line option' | 'exception having single parent cause'
        new RuntimeException('Something went wrong', new GradleException('Unknown command line option', new Exception('Total fail'))) | 'Total fail'                  | 'exception having multiple parent causes'
    }

    def "temporary working space directory is not created if Gradle user home directory is not provided by user"() {
        given:
        def gradleUserHomeDir = testDirectoryProvider.createDir('some/dir')

        when:
        createRunnerWithWorkingDirAndArgument().build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run({ it.gradleUserHome == gradleUserHomeDir }) >> new GradleExecutionResult(BUILD_OPERATION_PARAMETERS, "", null)
    }

    def "debug flag determines runtime mode passed to executor"() {
        given:
        def gradleUserHomeDir = testDirectoryProvider.createDir('some/dir')

        when:
        createRunnerWithWorkingDirAndArgument().withDebug(debug).build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run({ it.embedded == debug }) >> new GradleExecutionResult(BUILD_OPERATION_PARAMETERS, "", null)

        where:
        debug << [true, false]
    }

    def "debug flag is #description for system property value '#systemPropertyValue'"() {
        when:
        System.properties[DefaultGradleRunner.DEBUG_SYS_PROP] = systemPropertyValue

        then:
        createRunner().debug == debugEnabled

        where:
        systemPropertyValue | debugEnabled | description
        "true"              | true         | 'enabled'
        "false"             | false        | 'disabled'
        "test"              | false        | 'disabled'
    }

    def "throws exception if standard output is null"() {
        when:
        createRunner().forwardStdError(new StringWriter()).forwardStdOutput(null)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'standardOutput argument cannot be null'
    }

    def "throws exception if standard error is null"() {
        when:
        createRunner().forwardStdOutput(new StringWriter()).forwardStdError(null)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'standardError argument cannot be null'
    }

    def "standard output is passed on to executor"() {
        given:
        def standardOutput = new StringWriter()
        def gradleUserHomeDir = testDirectoryProvider.createDir('some/dir')

        when:
        createRunnerWithWorkingDirAndArgument()
            .forwardStdOutput(standardOutput)
            .build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run({ it.standardError == null && it.standardOutput != null }) >> new GradleExecutionResult(BUILD_OPERATION_PARAMETERS, "", null)
    }

    def "standard error is passed on to executor"() {
        given:
        def standardError = new StringWriter()
        def gradleUserHomeDir = testDirectoryProvider.createDir('some/dir')

        when:
        createRunnerWithWorkingDirAndArgument()
            .forwardStdError(standardError)
            .build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run({ it.standardError != null && it.standardOutput == null }) >> new GradleExecutionResult(BUILD_OPERATION_PARAMETERS, "", null)
    }

    def "standard output and error is passed on to executor"() {
        given:
        Writer standardOutput = new StringWriter()
        Writer standardError = new StringWriter()
        File gradleUserHomeDir = testDirectoryProvider.createDir('some/dir')

        when:
        createRunnerWithWorkingDirAndArgument()
            .forwardStdOutput(standardOutput)
            .forwardStdError(standardError)
            .build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run({ it.standardError != null && it.standardOutput != null }) >> new GradleExecutionResult(BUILD_OPERATION_PARAMETERS, "", null)
    }

    def "standard input is passed on to executor"() {
        given:
        def standardInput = new ByteArrayInputStream()
        def gradleUserHomeDir = testDirectoryProvider.createDir('some/dir')

        when:
        createRunnerWithWorkingDirAndArgument()
            .withStandardInput(standardInput)
            .build()

        then:
        1 * testKitDirProvider.getDir() >> gradleUserHomeDir
        1 * gradleExecutor.run({ it.standardInput != null }) >> new GradleExecutionResult(BUILD_OPERATION_PARAMETERS, "", null)
    }

    private DefaultGradleRunner createRunner() {
        new DefaultGradleRunner(gradleExecutor, testKitDirProvider)
    }

    private DefaultGradleRunner createRunnerWithWorkingDirAndArgument() {
        createRunner()
            .withProjectDir(workingDir)
            .withArguments(arguments)
            .withGradleVersion(GradleVersion.current().version) as DefaultGradleRunner
    }

    static GradleExecutionResult createGradleExecutionResult(Throwable throwable = null) {
        new GradleExecutionResult(BUILD_OPERATION_PARAMETERS, "this is some output", [], throwable)
    }

    private String getBasicDiagnosticsMessage() {
        """Gradle build executed in $workingDir.absolutePath with arguments $arguments

Output:
this is some output"""
    }
}
