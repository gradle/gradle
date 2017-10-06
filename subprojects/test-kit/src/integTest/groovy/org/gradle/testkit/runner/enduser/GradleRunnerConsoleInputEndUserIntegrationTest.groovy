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

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.*

class GradleRunnerConsoleInputEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def "can provide user input to standard in"() {
        buildFile << """
            apply plugin: 'groovy'

            dependencies {
                compile localGroovy()
                testCompile gradleTestKit()
                testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
            }

            ${jcenterRepository()}
        """

        when:
        file("src/test/groovy/Test.groovy") << """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class Test extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    def buildSrcDir = testProjectDir.newFolder('buildSrc', 'src', 'main', 'java')
                    def pluginFile = new File(buildSrcDir, 'BuildScanPlugin.java')
                    pluginFile << '''${buildScanPlugin()}'''
                    buildFile = testProjectDir.newFile('build.gradle')
                    buildFile << '''${buildScanPluginApplication()}'''
                }

                def "can write to standard input"() {
                    when:
                    def input = new ByteArrayInputStream(('yes' + System.getProperty('line.separator')).bytes)
                    System.setIn(input)
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('doSomething')
                        .withDebug($debug)
                        .withStandardInput(System.in)
                        .build()

                    then:
                    result.output.contains('${answerOutput(true)}')
                }
            }
        """

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
    }
}
