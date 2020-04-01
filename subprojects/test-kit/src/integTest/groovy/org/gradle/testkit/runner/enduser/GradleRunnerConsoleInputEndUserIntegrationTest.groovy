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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.*

class GradleRunnerConsoleInputEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'groovy'

            dependencies {
                testImplementation localGroovy()
                testImplementation gradleTestKit()
                testImplementation('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
            }

            ${jcenterRepository()}
        """
    }

    @ToBeFixedForInstantExecution(because = "gradle/instant-execution#270")
    def "can capture user input if standard input was provided"() {
        when:
        file("src/test/groovy/Test.groovy") << functionalTest(true, true)

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
    }

    @ToBeFixedForInstantExecution(because = "gradle/instant-execution#270")
    def "cannot capture user input if standard in was not provided"() {
        when:
        file("src/test/groovy/Test.groovy") << functionalTest(false, null)

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
    }

    static String functionalTest(boolean providesStandardInput, Boolean expectedAnswer) {
        """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class Test extends Specification {
                @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    def buildSrcDir = testProjectDir.newFolder('buildSrc', 'src', 'main', 'java')
                    def pluginFile = new File(buildSrcDir, 'BuildScanPlugin.java')
                    pluginFile << '''${buildScanPlugin()}'''
                    def settingsFile = testProjectDir.newFile('settings.gradle')
                    settingsFile << "rootProject.name = 'test'"
                    buildFile = testProjectDir.newFile('build.gradle')
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
                .withProjectDir(testProjectDir.root)
                .withArguments('$DUMMY_TASK_NAME')
                .withDebug($debug)
                .build()
        """
    }

    static String gradleRunnerWithStandardInput() {
        """
            GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('$DUMMY_TASK_NAME')
                .withDebug($debug)
                .withStandardInput(System.in)
                .build()
        """
    }
}
