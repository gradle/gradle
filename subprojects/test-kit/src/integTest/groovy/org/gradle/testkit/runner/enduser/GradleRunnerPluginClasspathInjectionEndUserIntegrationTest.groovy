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

package org.gradle.testkit.runner.enduser


import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.testkit.runner.fixtures.PluginUnderTest
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.embedded }) // These tests run builds that themselves run a build in a test worker with 'gradleTestKit()' dependency, which needs to pick up Gradle modules from a real distribution
class GradleRunnerPluginClasspathInjectionEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def plugin = new PluginUnderTest(testDirectory)

    def setup() {
        plugin.writeSourceFiles().writeBuildScript()
        buildFile << """
            task createClasspathManifest {
                def outputDir = file("\$buildDir/\$name")

                inputs.files sourceSets.main.runtimeClasspath
                outputs.dir outputDir

                doLast {
                    outputDir.mkdirs()
                    file("\$outputDir/plugin-classpath.txt").text = sourceSets.main.runtimeClasspath.join("\\n")
                }
            }

            dependencies {
                implementation localGroovy()
                testImplementation gradleTestKit()
                testImplementation files(createClasspathManifest)
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

    def "can test plugin and custom task as external files by adding them to the build script's classpath"() {
        when:
        file("src/test/groovy/Test.groovy") << """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import spock.lang.Specification
            import spock.lang.TempDir

            class Test extends Specification {
                @TempDir File testProjectDir
                File buildFile

                def setup() {
                    new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'test'"
                    buildFile = new File(testProjectDir, 'build.gradle')
                    def pluginClasspath = getClass().classLoader.getResource("plugin-classpath.txt")
                      .readLines()
                      .collect { it.replace('\\\\', '\\\\\\\\') } // escape backslashes in Windows paths
                      .collect { "'\$it'" }
                      .join(", ")

                    buildFile << \"\"\"
                        buildscript {
                            dependencies {
                                classpath files(\$pluginClasspath)
                            }
                        }
                    \"\"\"
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << 'apply plugin: "$plugin.id"'

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir)
                        .withArguments('helloWorld')
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
        new JUnitXmlTestExecutionResult(projectDir).totalNumberOfTestClassesExecuted > 0
    }

    def "can test plugin and custom task as external files by providing them as classpath through GradleRunner API"() {
        when:
        file("src/test/groovy/Test.groovy") << """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import spock.lang.Specification
            import spock.lang.TempDir

            class Test extends Specification {
                @TempDir File testProjectDir
                File buildFile
                List<File> pluginClasspath

                def setup() {
                    new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'test'"
                    buildFile = new File(testProjectDir, 'build.gradle')
                    pluginClasspath = getClass().classLoader.getResource("plugin-classpath.txt")
                      .readLines()
                      .collect { new File(it) }
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << '''$plugin.useDeclaration'''

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir)
                        .withArguments('helloWorld')
                        .withPluginClasspath(pluginClasspath)
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
        new JUnitXmlTestExecutionResult(projectDir).totalNumberOfTestClassesExecuted > 0
    }

}
