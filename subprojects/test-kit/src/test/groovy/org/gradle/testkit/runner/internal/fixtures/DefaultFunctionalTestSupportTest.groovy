/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testkit.runner.internal.fixtures

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.fixtures.FunctionalTestSupport
import org.gradle.testkit.runner.fixtures.file.TestFile
import org.gradle.testkit.runner.internal.file.TemporaryDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.gradle.wrapper.GradleUserHomeLookup
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DefaultFunctionalTestSupportTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    @Rule
    SetSystemProperties setSystemProperties = new SetSystemProperties(
        (NativeServices.NATIVE_DIR_OVERRIDE): buildContext.nativeServicesDir.absolutePath,
        (GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY): buildContext.gradleUserHomeDir.absolutePath
    )

    TemporaryDirectoryProvider temporaryDirectoryProvider = new TestNameTestTemporaryDirectoryProvider()
    FunctionalTestSupport functionalTestSupport

    def setup() {
        functionalTestSupport = new DefaultFunctionalTestSupport(temporaryDirectoryProvider)
        functionalTestSupport.initialize()
        useGradleDistUnderDevelopment()
    }

    def "creates basic setup"() {
        expect:
        functionalTestSupport.testDirectory == new TestFile(temporaryDirectoryProvider.directory)
        !functionalTestSupport.buildFile.exists()
        !functionalTestSupport.settingsFile.exists()
        functionalTestSupport.gradleRunner.projectDir == temporaryDirectoryProvider.directory
    }

    def "can create and append to build file"() {
        given:
        String initialBuildFileContent = """
            version = '1.0'
        """
        String laterBuildFileContent = """
            task abc
        """

        when:
        functionalTestSupport.buildFile << initialBuildFileContent

        then:
        functionalTestSupport.buildFile.exists()
        functionalTestSupport.buildFile.text == initialBuildFileContent

        when:
        functionalTestSupport.buildFile << laterBuildFileContent

        then:
        functionalTestSupport.buildFile.exists()
        functionalTestSupport.buildFile.text == initialBuildFileContent + laterBuildFileContent
    }

    def "can create and append to settings file"() {
        given:
        String initialSettingsFileContent = """
            rootProject.name = 'hello'
        """
        String laterSettingsFileContent = """
            include 'a', 'b', 'c'
        """

        when:
        functionalTestSupport.settingsFile << initialSettingsFileContent

        then:
        functionalTestSupport.settingsFile.exists()
        functionalTestSupport.settingsFile.text == initialSettingsFileContent

        when:
        functionalTestSupport.settingsFile << laterSettingsFileContent

        then:
        functionalTestSupport.settingsFile.exists()
        functionalTestSupport.settingsFile.text == initialSettingsFileContent + laterSettingsFileContent
    }

    def "can execute successful build"() {
        given:
        functionalTestSupport.buildFile << successfulHelloWorldTask()

        when:
        def result = functionalTestSupport.succeeds('helloWorld')

        then:
        result.task(':helloWorld').outcome == SUCCESS
        result.output.contains('Hello World!')
    }

    def "can execute failing build"() {
        given:
        functionalTestSupport.buildFile << failingHelloWorldTask()

        when:
        def result = functionalTestSupport.fails('helloWorld')

        then:
        result.task(':helloWorld').outcome == FAILED
        result.output.contains('expected failure')

        when:
        result = functionalTestSupport.fails(['helloWorld'])

        then:
        result.task(':helloWorld').outcome == FAILED
        result.output.contains('expected failure')
    }

    def "can configure GradleRunner instance"() {
        given:
        functionalTestSupport.buildFile << successfulHelloWorldTask()
        StringWriter output = new StringWriter()

        when:
        functionalTestSupport.gradleRunner.forwardStdOutput(output)
        def result = functionalTestSupport.succeeds('helloWorld')

        then:
        result.task(':helloWorld').outcome == SUCCESS
        output.toString().contains('Hello World!')

        when:
        functionalTestSupport.gradleRunner.forwardStdOutput(output)
        result = functionalTestSupport.succeeds(['helloWorld'])

        then:
        result.task(':helloWorld').outcome == SUCCESS
        output.toString().contains('Hello World!')
    }

    class TestNameTestTemporaryDirectoryProvider implements TemporaryDirectoryProvider {
        @Override
        void create() {
            // created by JUnit rule
        }

        @Override
        void destroy() {
            // destroyed by JUnit rule
        }

        @Override
        File getDirectory() {
            temporaryFolder.testDirectory
        }
    }

    private void useGradleDistUnderDevelopment() {
        functionalTestSupport.gradleRunner.withGradleInstallation(buildContext.gradleHomeDir)
    }

    static IntegrationTestBuildContext getBuildContext() {
        IntegrationTestBuildContext.INSTANCE
    }

    static String successfulHelloWorldTask() {
        """
            task helloWorld {
                doLast {
                    println 'Hello World!'
                }
            }
        """
    }

    static String failingHelloWorldTask() {
        """
            task helloWorld {
                doLast {
                    throw new GradleException('expected failure')
                }
            }
        """
    }
}
