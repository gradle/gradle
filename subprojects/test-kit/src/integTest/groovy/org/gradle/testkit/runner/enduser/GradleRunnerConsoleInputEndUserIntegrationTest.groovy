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

package org.gradle.testkit.runner.enduser

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.DUMMY_TASK_NAME
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.PROMPT
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.YES
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.answerOutput
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.buildScanPlugin
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.buildScanPluginApplication

@IgnoreIf({ GradleContextualExecuter.embedded }) // These tests run builds that themselves run a build in a test worker with 'gradleTestKit()' dependency, which needs to pick up Gradle modules from a real distribution
class GradleRunnerConsoleInputEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'groovy'

            dependencies {
                testImplementation localGroovy()
                testImplementation gradleTestKit()
            }

            testing {
                suites {
                    test {
                        useSpock()
                    }
                }
            }

            ${mavenCentralRepository()}
        """
    }

    def "can capture user input if standard input was provided"() {
        when:
        file("src/test/groovy/Test.groovy") << functionalTest(true, true)

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
        new JUnitXmlTestExecutionResult(projectDir).totalNumberOfTestClassesExecuted > 0
    }

    def "cannot capture user input if standard in was not provided"() {
        when:
        file("src/test/groovy/Test.groovy") << functionalTest(false, null)

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
        new JUnitXmlTestExecutionResult(projectDir).totalNumberOfTestClassesExecuted > 0
    }

    static String functionalTest(boolean providesStandardInput, Boolean expectedAnswer) {
        """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import spock.lang.Specification
            import spock.lang.TempDir

            class Test extends Specification {
                @TempDir File testProjectDir
                File buildFile

                def setup() {
                    def buildSrcDir = new File(testProjectDir, 'buildSrc/src/main/java').tap { mkdirs() }
                    def pluginFile = new File(buildSrcDir, 'BuildScanPlugin.java')
                    pluginFile << '''${buildScanPlugin()}'''
                    def settingsFile = new File(testProjectDir, 'settings.gradle')
                    settingsFile << "rootProject.name = 'test'"
                    buildFile = new File(testProjectDir, 'build.gradle')
                    buildFile << '''${buildScanPluginApplication()}'''
                }

                def "capture user input"() {
                    when:
                    ${providesStandardInput ? provideYesAnswerToStandardInput() : ''}
                    def result = ${providesStandardInput ? gradleRunnerWithStandardInput() : gradleRunnerWithoutStandardInput()}

                    then:
                    ${providesStandardInput ? "result.output.contains('$PROMPT')" : "!result.output.contains('$PROMPT')"}
                    result.output.contains('${answerOutput(expectedAnswer)}')
                }
            }
        """
    }

    static String provideYesAnswerToStandardInput() {
        """
            def input = new ByteArrayInputStream(('$YES' + System.getProperty('line.separator')).bytes)
            System.setIn(input)
        """
    }

    static String gradleRunnerWithoutStandardInput() {
        """
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('$DUMMY_TASK_NAME')
                .withDebug($debug)
                .build()
        """
    }

    static String gradleRunnerWithStandardInput() {
        """
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('$DUMMY_TASK_NAME')
                .withDebug($debug)
                .withStandardInput(System.in)
                .build()
        """
    }
}
