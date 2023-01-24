/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.tasks.TasksWithInputsAndOutputs

import java.util.concurrent.CopyOnWriteArrayList

class ConfigurationCacheScriptTaskDefinitionIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements TasksWithInputsAndOutputs {

    def "task can have actions defined using Groovy script closures"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """
        [file("a/build.gradle"), file("b/build.gradle")].each {
            it << """
                tasks.register("some") {
                    doFirst {
                        println("FIRST")
                    }
                    doLast {
                        println("LAST")
                    }
                }
                tasks.register("other") {
                    doFirst {
                        println("OTHER")
                    }
                }
            """
        }

        when:
        configurationCacheRun ":a:some"

        then:
        result.groupedOutput.task(":a:some").assertOutputContains("FIRST").assertOutputContains("LAST")

        when:
        configurationCacheRun ":a:some"

        then:
        result.groupedOutput.task(":a:some").assertOutputContains("FIRST").assertOutputContains("LAST")

        when: // rebuild task graph, with tasks from the same set of scripts
        configurationCacheRun ":a:some", ":a:other"

        then:
        result.groupedOutput.task(":a:some").assertOutputContains("FIRST").assertOutputContains("LAST")
        result.groupedOutput.task(":a:other").assertOutputContains("OTHER")

        when:
        configurationCacheRun ":a:some", ":a:other"

        then:
        result.groupedOutput.task(":a:some").assertOutputContains("FIRST").assertOutputContains("LAST")
        result.groupedOutput.task(":a:other").assertOutputContains("OTHER")

        when: // rebuild task graph, with tasks from a different set of scripts
        configurationCacheRun ":b:some"

        then:
        result.groupedOutput.task(":b:some").assertOutputContains("FIRST").assertOutputContains("LAST")

        when:
        configurationCacheRun ":b:some"

        then:
        result.groupedOutput.task(":b:some").assertOutputContains("FIRST").assertOutputContains("LAST")
    }

    def "task can have actions defined using anonymous Groovy script actions"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst(new Action<Task>() {
                    void execute(task) {
                        println("FIRST")
                    }
                })
                doLast(new Action<Task>() {
                    void execute(task) {
                        println("LAST")
                    }
                })
            }
            tasks.register("other") {
                doFirst(new Action<Task>() {
                    void execute(task) {
                        println("OTHER")
                    }
                })
            }
        """

        when:
        configurationCacheRun "some"

        then:
        result.groupedOutput.task(":some").assertOutputContains("FIRST").assertOutputContains("LAST")

        when:
        configurationCacheRun "some"

        then:
        result.groupedOutput.task(":some").assertOutputContains("FIRST").assertOutputContains("LAST")

        when:
        configurationCacheRun "some", "other"

        then:
        result.groupedOutput.task(":some").assertOutputContains("FIRST").assertOutputContains("LAST")
        result.groupedOutput.task(":other").assertOutputContains("OTHER")

        when:
        configurationCacheRun "some", "other"

        then:
        result.groupedOutput.task(":some").assertOutputContains("FIRST").assertOutputContains("LAST")
        result.groupedOutput.task(":other").assertOutputContains("OTHER")
    }

    def "task can have actions defined using Kotlin script lambdas"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """
        [file("a/build.gradle.kts"), file("b/build.gradle.kts")].each {
            it << """
                tasks.register("some") {
                    doFirst {
                        println("FIRST")
                    }
                    doLast {
                        println("LAST")
                    }
                }

                tasks.register("other") {
                    doFirst {
                        println("OTHER")
                    }
                }
            """
        }

        when:
        configurationCacheRun ":a:some"

        then:
        result.groupedOutput.task(":a:some").assertOutputContains("FIRST").assertOutputContains("LAST")

        when:
        configurationCacheRun ":a:some"

        then:
        result.groupedOutput.task(":a:some").assertOutputContains("FIRST").assertOutputContains("LAST")

        when: // rebuild task graph, with tasks from the same set of scripts
        configurationCacheRun ":a:some", ":a:other"

        then:
        result.groupedOutput.task(":a:some").assertOutputContains("FIRST").assertOutputContains("LAST")
        result.groupedOutput.task(":a:other").assertOutputContains("OTHER")

        when:
        configurationCacheRun ":a:some", ":a:other"

        then:
        result.groupedOutput.task(":a:some").assertOutputContains("FIRST").assertOutputContains("LAST")
        result.groupedOutput.task(":a:other").assertOutputContains("OTHER")

        when: // rebuild task graph, with tasks from a different set of scripts
        configurationCacheRun ":b:some"

        then:
        result.groupedOutput.task(":b:some").assertOutputContains("FIRST").assertOutputContains("LAST")

        when:
        configurationCacheRun ":b:some"

        then:
        result.groupedOutput.task(":b:some").assertOutputContains("FIRST").assertOutputContains("LAST")
    }

    def "each task receives its own copy of outer Groovy closure state"() {
        given:
        buildFile << """
            def values1 = new ${CopyOnWriteArrayList.name}()
            tasks.register("one") {
                def values2 = new ${CopyOnWriteArrayList.name}()
                doFirst {
                    values1.add(1)
                    values2.add(2)
                }
                doLast {
                    println "values1=" + values1
                    println "values2=" + values2
                }
            }
            tasks.register("two") {
                doFirst {
                    values1.add(12)
                    println "values1=" + values1
                }
            }
        """

        when:
        configurationCacheRun ":one", ":two"

        then:
        result.groupedOutput.task(":one").assertOutputContains("values1=[1]")
        result.groupedOutput.task(":one").assertOutputContains("values2=[2]")
        result.groupedOutput.task(":two").assertOutputContains("values1=[12]")

        when:
        configurationCacheRun ":one", ":two"

        then:
        result.groupedOutput.task(":one").assertOutputContains("values1=[1]")
        result.groupedOutput.task(":one").assertOutputContains("values2=[2]")
        result.groupedOutput.task(":two").assertOutputContains("values1=[12]")
    }

    def "problem when closure defined in Kotlin script captures state from the script"() {
        given:
        buildKotlinFile << """
            val message = "message"
            tasks.register("some") {
                doFirst {
                    println(message)
                }
            }
        """

        when:
        configurationCacheFails ":some"

        then:
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(
                "Task `:some` of type `org.gradle.api.DefaultTask`: cannot serialize Gradle script object references as these are not supported with the configuration cache."
            )
            withProblemsWithStackTraceCount(0)
        }
    }

    def "task with type declared in Groovy script is up-to-date when no inputs have changed"() {
        given:
        taskTypeWithOutputFileProperty()
        buildFile << """
            tasks.register("a", FileProducer) {
                output = layout.buildDirectory.file("out.txt")
            }
        """
        def outputFile = file("build/out.txt")

        when:
        configurationCacheRun "a"

        then:
        result.assertTasksExecutedAndNotSkipped(":a")
        outputFile.assertIsFile()

        when:
        configurationCacheRun "a"

        then:
        result.assertTasksSkipped(":a")

        when:
        outputFile.delete()
        configurationCacheRun "a"

        then:
        result.assertTasksExecutedAndNotSkipped(":a")
        outputFile.assertIsFile()
    }

    def "task with type declared in Kotlin script is up-to-date when no inputs have changed"() {
        given:
        kotlinTaskTypeWithOutputFileProperty()
        buildKotlinFile << """
            tasks.register<FileProducer>("a") {
                output.set(layout.buildDirectory.file("out.txt"))
            }
        """
        def outputFile = file("build/out.txt")

        when:
        configurationCacheRun "a"

        then:
        result.assertTasksExecutedAndNotSkipped(":a")
        outputFile.assertIsFile()

        when:
        configurationCacheRun "a"

        then:
        result.assertTasksSkipped(":a")

        when:
        outputFile.delete()
        configurationCacheRun "a"

        then:
        result.assertTasksExecutedAndNotSkipped(":a")
        outputFile.assertIsFile()
    }

    def "name conflicts of types declared in Groovy scripts"() {
        given:
        settingsFile << """include("a", "b")"""
        file("a/build.gradle") << """

            tasks.register("some") {
                doLast { println("A") }
            }
        """
        file("b/build.gradle") << """

            tasks.register("some") {
                doLast { println("B") }
            }
        """

        when:
        configurationCacheRun "some"

        then:
        outputContains("A")
        outputContains("B")

        when:
        configurationCacheRun "some"

        then:
        outputContains("A")
        outputContains("B")
    }

    def "name conflicts of types declared in Kotlin scripts"() {
        given:
        settingsFile << """include("a", "b")"""
        file("a/build.gradle.kts") << """

            tasks.register("some") {
                doLast { println("A") }
            }
        """
        file("b/build.gradle.kts") << """

            tasks.register("some") {
                doLast { println("B") }
            }
        """

        when:
        configurationCacheRun "some"

        then:
        outputContains("A")
        outputContains("B")

        when:
        configurationCacheRun "some"

        then:
        outputContains("A")
        outputContains("B")
    }
}
