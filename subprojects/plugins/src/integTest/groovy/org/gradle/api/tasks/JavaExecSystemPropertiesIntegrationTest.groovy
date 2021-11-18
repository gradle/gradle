/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaExecSystemPropertiesIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        file("src/main/java/org/gradle/demo/Greeter.java") << """package org.gradle.demo;

    public class Greeter {
        public static String getGreeting() {
            return "Hello, " + System.getProperty("name", "unidentified person") + "!";
        }
        public static void main(String... args) {
            System.out.println(getGreeting());
        }
    }
"""
        file("src/test/java/org/gradle/demo/GreeterTest.java") << """
package org.gradle.demo;

    public class GreeterTest {
        @org.junit.Test public void testGreeting() {
            org.junit.Assert.assertEquals("Hello, Aditi!", new Greeter().getGreeting());
        }
    }
"""
        buildFile << """
            apply plugin: 'application'

            application {
                mainClass = 'org.gradle.demo.Greeter'
            }

            repositories {
                 ${mavenCentralRepository()}
            }

            dependencies {
                 testImplementation 'junit:junit:4.13'
            }

        """
    }

    def "can use a provider as a system property value"() {
        buildFile << """
            tasks.named('run') {
                systemProperty('name', providers.provider { "Aditi" })
            }
        """

        when:
        run "run"

        then:
        outputContains ("Hello, Aditi!")
    }

    def "up-to-date checking considers providers in system properties"() {
        buildFile << """
            tasks.named('test', Test) {
                systemProperty('name', providers.systemProperty('name'))
            }
        """

        when:
        run "test", "-Dname=Aditi"

        then:
        executedAndNotSkipped (":test")

        when:
        run "test", "-Dname=Aditi"

        then:
        skipped (":test")

        when:
        fails "test", "-Dname=Wajeeha"

        then:
        executedAndNotSkipped (":test")
        outputContains "org.gradle.demo.GreeterTest > testGreeting FAILED"

    }

    def "tasks inputs are tracked when using providers as system property values"() {
        buildFile << """
            def buildName = tasks.register("buildName", BuildName) {
                outputFile = layout.buildDirectory.file("name.txt")
                person = providers.systemProperty("name").orElse("unidentified person")
            }

            abstract class BuildName extends DefaultTask {
                @Input
                abstract Property<String> getPerson()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void execute() {
                    outputFile.asFile.get().text = person.get()
                }
            }

            tasks.named('run') {
                systemProperty('name', buildName.map { it.outputFile.asFile.get().text })
            }
        """

        when:
        run "run", "-Dname=Aditi"

        then:
        executedAndNotSkipped(":buildName")
        outputContains("Hello, Aditi!")

    }

    def "system properties can use providers with ExecOperations"() {
        buildFile << """
            def buildName = tasks.register("exec", MyExec) {
                classpath.from(sourceSets.main.runtimeClasspath)
                outputFile = layout.buildDirectory.file("name.txt")
                person = providers.systemProperty("name").orElse("unidentified person")
            }

            abstract class MyExec extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getClasspath()

                @Input
                abstract Property<String> getPerson()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @Inject
                abstract ExecOperations getExecOperations()

                @TaskAction
                void execute() {
                    outputFile.asFile.get().text = person.get()
                    execOperations.javaexec {
                        it.classpath(this.classpath)
                        it.mainClass.set("org.gradle.demo.Greeter")

                        // This is what we want to test, "person" is a Provider<String>
                        it.systemProperty("name", person)
                    }
                }
            }
        """

        when:
        run "exec", "-Dname=Aditi"

        then:
        executedAndNotSkipped(":exec")
        outputContains("Hello, Aditi!")
    }
}
