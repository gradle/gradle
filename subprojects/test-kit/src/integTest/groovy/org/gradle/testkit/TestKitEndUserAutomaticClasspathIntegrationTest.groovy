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

package org.gradle.testkit

class TestKitEndUserAutomaticClasspathIntegrationTest extends AbstractTestKitEndUserIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'java-gradle-plugin'
        """
    }

    def "can test plugin and custom task as external files by using default conventions from Java Gradle plugin development plugin"() {
        file("src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy") << helloWorldPluginJavaClass()
        file("src/main/groovy/org/gradle/test/HelloWorld.groovy") << helloWorldJavaClass()
        file("src/main/resources/META-INF/gradle-plugins/com.company.helloworld.properties") << pluginProperties()
        writeTest functionalTestGroovyClass()

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':test')
        assertDaemonsAreStopping()

        cleanup:
        killDaemons()
    }

    def "can test plugin and custom task as external files by configuring custom test source set with Java Gradle plugin development plugin"() {
        buildFile << """
            sourceSets {
                functionalTest {
                    groovy.srcDir file('src/functionalTest/groovy')
                    resources.srcDir file('src/functionalTest/resources')
                    compileClasspath += sourceSets.main.output + configurations.testRuntime
                    runtimeClasspath += output + compileClasspath
                }
            }

            task functionalTest(type: Test) {
                testClassesDir = sourceSets.functionalTest.output.classesDir
                classpath = sourceSets.functionalTest.runtimeClasspath
            }

            check.dependsOn functionalTest

            gradlePlugin {
                testSourceSets sourceSets.functionalTest
            }
        """

        file("src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy") << helloWorldPluginJavaClass()
        file("src/main/groovy/org/gradle/test/HelloWorld.groovy") << helloWorldJavaClass()
        file("src/main/resources/META-INF/gradle-plugins/com.company.helloworld.properties") << pluginProperties()
        file("src/functionalTest/groovy/org/gradle/test/BuildLogicFunctionalTest.groovy") << functionalTestGroovyClass()

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':functionalTest')
        assertDaemonsAreStopping()

        cleanup:
        killDaemons()
    }


    private String helloWorldPluginJavaClass() {
        """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld', type: HelloWorld)
                }
            }
        """
    }

    private void helloWorldJavaClass() {
        """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Hello world!'
                }
            }
        """
    }

    private void pluginProperties() {
        """
            implementation-class=org.gradle.test.HelloWorldPlugin
        """
    }

    private void functionalTestGroovyClass() {
        """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << \"\"\"
                        plugins {
                            id 'com.company.helloworld'
                        }
                    \"\"\"

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')
                        .withPluginClasspath()
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """
    }
}
