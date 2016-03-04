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

import org.gradle.testkit.runner.fixtures.PluginUnderTest

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
                compile localGroovy()
                testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
                testCompile gradleTestKit()
                testCompile files(createClasspathManifest)
            }

            repositories {
                jcenter()
            }
        """
    }

    def "can test plugin and custom task as external files by adding them to the build script's classpath"() {
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
                    buildFile = testProjectDir.newFile('build.gradle')
                    def pluginClasspath = getClass().classLoader.findResource("plugin-classpath.txt")
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
                        .withProjectDir(testProjectDir.root)
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
    }

    def "can test plugin and custom task as external files by providing them as classpath through GradleRunner API"() {
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
                List<File> pluginClasspath

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                    pluginClasspath = getClass().classLoader.findResource("plugin-classpath.txt")
                      .readLines()
                      .collect { new File(it) }
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << '''$plugin.useDeclaration'''

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
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
    }

}
