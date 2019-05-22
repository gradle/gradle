/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.CoreMatchers
import spock.lang.Issue

class BuildAggregationIntegrationTest extends AbstractIntegrationSpec {

    def canExecuteAnotherBuildFromBuild() {
        when:
        buildFile << '''
            assert gradle.parent == null
            task build(type: GradleBuild) {
                dir = 'other'
                tasks = ['dostuff']
            }
'''

        and:
        file('other/settings.gradle') << "rootProject.name = 'other'"
        file('other/build.gradle') << '''
            assert gradle.parent != null
            task dostuff {
                doLast {
                    assert gradle.parent != null
                }
            }
'''

        then:
        succeeds "build"
    }

    def treatsBuildSrcProjectAsANestedBuild() {
        when:
        buildFile << '''
            assert gradle.parent == null
            task build
'''

        file('buildSrc/build.gradle') << '''
            apply plugin: 'java'
            assert gradle.parent != null
            classes {
                doLast {
                    assert gradle.parent != null
                }
            }
'''

        then:
        succeeds "build"
    }

    def reportsNestedBuildFailure() {
        when:
        file('other/settings.gradle') << "rootProject.name = 'other'"
        TestFile other = file('other/other.gradle') << '''
            throw new ArithmeticException('broken')
'''

        buildFile << '''
            task build(type: GradleBuild) {
                buildFile = 'other/other.gradle'
            }
'''

        then:
        fails "build"

        and:
        failure.assertHasFileName("Build file '${other}'")
        failure.assertHasLineNumber(2)
        failure.assertThatDescription(CoreMatchers.startsWith("A problem occurred evaluating project ':other'"))
        failure.assertHasCause('broken')
    }

    def reportsBuildSrcFailure() {
        when:
        file('buildSrc/src/main/java/Broken.java') << 'broken!'

        then:
        fails()

        and:
        failure.assertHasDescription("Execution failed for task ':buildSrc:compileJava'.")
    }

    @Issue("https://issues.gradle.org//browse/GRADLE-3052")
    def buildTaskCanHaveInputsAndOutputs() {
        file("input") << "foo"
        settingsFile << "rootProject.name = 'proj'"
        buildFile << """
            class UpperFile extends DefaultTask {
                @InputFile
                File input

                @OutputFile
                File output

                @TaskAction
                void upper() {
                  output.text = input.text.toUpperCase()
                }
            }

            task upper(type: UpperFile) {
                input = file("input")
                output = file("output")
            }

            task build(type: GradleBuild) {
              dependsOn upper
              tasks = ["upper"]
              outputs.file "build.gradle" // having an output (or input) triggers the bug
            }
        """

        when:
        succeeds "build"

        then:
        executed ":upper", ":build", ":proj:upper"
        skippedTasks == [":proj:upper"] as Set
    }
}
