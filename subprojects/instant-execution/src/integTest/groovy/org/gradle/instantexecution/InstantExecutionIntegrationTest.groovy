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

package org.gradle.instantexecution

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import org.slf4j.Logger
import spock.lang.Unroll

import javax.inject.Inject

class InstantExecutionIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "instant execution for help on empty project"() {
        given:
        instantRun "help"
        def firstRunOutput = result.normalizedOutput.replace('Calculating task graph as no instant execution cache is available for tasks: help', '')

        when:
        instantRun "help"

        then:
        firstRunOutput == result.normalizedOutput.replace('Reusing instant execution cache. This is not guaranteed to work in any way.', '')
    }

    def "restores some details of the project structure"() {
        def fixture = new BuildOperationsFixture(executer, temporaryFolder)

        settingsFile << """
            rootProject.name = 'thing'
        """

        when:
        instantRun "help"

        then:
        def event = fixture.first(LoadProjectsBuildOperationType)
        event.result.rootProject.name == 'thing'

        when:
        instantRun "help"

        then:
        def event2 = fixture.first(LoadProjectsBuildOperationType)
        event2.result.rootProject.name == 'thing'
    }

    def "does not configure build when task graph is already cached for requested tasks"() {

        def instantExecution = newInstantExecutionFixture()

        given:
        buildFile << """
            println "running build script"
            
            class SomeTask extends DefaultTask {
                SomeTask() {
                    println("create task")
                }
            }
            task a(type: SomeTask) {
                println("configure task")
            }
            task b {
                dependsOn a
            }
        """

        when:
        instantRun "a"

        then:
        instantExecution.assertStateStored()
        outputContains("running build script")
        outputContains("create task")
        outputContains("configure task")
        result.assertTasksExecuted(":a")

        when:
        instantRun "a"

        then:
        instantExecution.assertStateLoaded()
        outputDoesNotContain("running build script")
        outputDoesNotContain("create task")
        outputDoesNotContain("configure task")
        result.assertTasksExecuted(":a")

        when:
        instantRun "b"

        then:
        instantExecution.assertStateStored()
        outputContains("running build script")
        outputContains("create task")
        outputContains("configure task")
        result.assertTasksExecuted(":a", ":b")

        when:
        instantRun "a"

        then:
        instantExecution.assertStateLoaded()
        outputDoesNotContain("running build script")
        outputDoesNotContain("create task")
        outputDoesNotContain("configure task")
        result.assertTasksExecuted(":a")
    }

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "instant execution for task in multiple projects"() {
        server.start()

        given:
        settingsFile << """
            include 'a', 'b', 'c'
        """
        buildFile << """
            class SlowTask extends DefaultTask {
                @TaskAction
                def go() {
                    ${server.callFromBuildUsingExpression("project.name")}
                }
            }

            subprojects {
                tasks.create('slow', SlowTask)
            }
            project(':a') {
                tasks.slow.dependsOn(project(':b').tasks.slow, project(':c').tasks.slow)
            }
        """

        when:
        server.expectConcurrent("b", "c")
        server.expectConcurrent("a")
        instantRun "slow", "--parallel"

        then:
        noExceptionThrown()

        when:
        def pendingCalls = server.expectConcurrentAndBlock("b", "c")
        server.expectConcurrent("a")

        def buildHandle = executer.withTasks("slow", "--parallel", "--max-workers=3", INSTANT_EXECUTION_PROPERTY).start()
        pendingCalls.waitForAllPendingCalls()
        pendingCalls.releaseAll()
        buildHandle.waitForFinish()

        then:
        noExceptionThrown()
    }

    def "instant execution for multi-level subproject"() {
        given:
        settingsFile << """
            include 'a:b', 'a:c'
        """
        instantRun ":a:b:help", ":a:c:help"
        def firstRunOutput = result.groupedOutput

        when:
        instantRun ":a:b:help", ":a:c:help"

        then:
        result.groupedOutput.task(":a:b:help").output == firstRunOutput.task(":a:b:help").output
        result.groupedOutput.task(":a:c:help").output == firstRunOutput.task(":a:c:help").output
    }

    def "restores task fields whose value is an object graph with cycles"() {
        buildFile << """
            class SomeBean {
                String value 
                SomeBean parent
                SomeBean child
                
                SomeBean(String value) {
                    println("creating bean")
                    this.value = value
                }
            }

            class SomeTask extends DefaultTask {
                final SomeBean bean
                
                SomeTask() {
                    bean = new SomeBean("default")
                    bean.parent = new SomeBean("parent")
                    bean.parent.child = bean
                    bean.parent.parent = bean.parent
                }

                @TaskAction
                void run() {
                    println "bean.value = " + bean.value
                    println "bean.parent.value = " + bean.parent.value
                    println "same reference = " + (bean.parent.child == bean)
                }
            }

            task ok(type: SomeTask) {
                bean.value = "child"
            }
        """

        when:
        instantRun "ok"

        then:
        result.output.count("creating bean") == 2

        when:
        instantRun "ok"

        then:
        outputDoesNotContain("creating bean")
        outputContains("bean.value = child")
        outputContains("bean.parent.value = parent")
        outputContains("same reference = true")
    }

    @Unroll
    def "restores task fields whose value is instance of #type"() {
        buildFile << """
            class SomeBean {
                ${type} value 
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final ${type} value
                
                SomeTask() {
                    value = ${reference}
                    bean.value = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value
                    println "bean.value = " + bean.value
                }
            }

            task ok(type: SomeTask)
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("this.value = ${output}")
        outputContains("bean.value = ${output}")

        where:
        type                   | reference                | output
        String.name            | "'value'"                | "value"
        String.name            | "null"                   | "null"
        Boolean.name           | "true"                   | "true"
        boolean.name           | "true"                   | "true"
        Byte.name              | "12"                     | "12"
        byte.name              | "12"                     | "12"
        Short.name             | "12"                     | "12"
        short.name             | "12"                     | "12"
        Integer.name           | "12"                     | "12"
        int.name               | "12"                     | "12"
        Long.name              | "12"                     | "12"
        long.name              | "12"                     | "12"
        Float.name             | "12.1"                   | "12.1"
        float.name             | "12.1"                   | "12.1"
        Double.name            | "12.1"                   | "12.1"
        double.name            | "12.1"                   | "12.1"
        Class.name             | "SomeBean"               | "class SomeBean"
        "List<String>"         | "['a', 'b', 'c']"        | "[a, b, c]"
        "Set<String>"          | "['a', 'b', 'c'] as Set" | "[a, b, c]"
        "Map<String, Integer>" | "[a: 1, b: 2]"           | "[a:1, b:2]"
    }

    @Unroll
    def "restores task fields whose value is service of type #type"() {
        buildFile << """
            class SomeBean {
                ${type} value 
            }

            class SomeTask extends DefaultTask {
                final SomeBean bean = new SomeBean()
                ${type} value

                @TaskAction
                void run() {
                    value.${invocation}
                    bean.value.${invocation}
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        noExceptionThrown()

        where:
        type               | reference | invocation
        Logger.name        | "logger"  | "info('hi')"
        ObjectFactory.name | "objects" | "newInstance(SomeBean)"
    }

    @Unroll
    def "restores task fields whose value is provider of type #type"() {
        buildFile << """
            import ${Inject.name}

            class SomeBean {
                ${type} value
            }

            class SomeTask extends DefaultTask {
                final SomeBean bean = project.objects.newInstance(SomeBean)
                ${type} value

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                    println "bean.value = " + bean.value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("this.value = ${output}")
        outputContains("bean.value = ${output}")

        where:
        type               | reference                                 | output
        "Provider<String>" | "providers.provider { 'value' }"          | "value"
        "Provider<String>" | "providers.provider { null }"             | "null"
        "Provider<String>" | "objects.property(String).value('value')" | "value"
        "Provider<String>" | "objects.property(String)"                | "null"
    }

    @Unroll
    def "restores task fields whose value is property of type #type"() {
        buildFile << """
            import ${Inject.name}

            class SomeBean {
                final ${type} value

                @Inject
                SomeBean(ObjectFactory objects) {
                    value = ${factory}
                }
            }

            class SomeTask extends DefaultTask {
                final SomeBean bean = project.objects.newInstance(SomeBean)
                final ${type} value

                @Inject
                SomeTask(ObjectFactory objects) {
                    value = ${factory}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                    println "bean.value = " + bean.value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        def expected = output instanceof File ? file(output.path) : output
        outputContains("this.value = ${expected}")
        outputContains("bean.value = ${expected}")

        where:
        type                  | factory                       | reference     | output
        "Property<String>"    | "objects.property(String)"    | "'value'"     | "value"
        "Property<String>"    | "objects.property(String)"    | "null"        | "null"
        "DirectoryProperty"   | "objects.directoryProperty()" | "file('abc')" | new File('abc')
        "DirectoryProperty"   | "objects.directoryProperty()" | "null"        | "null"
        "RegularFileProperty" | "objects.fileProperty()"      | "file('abc')" | new File('abc')
        "RegularFileProperty" | "objects.fileProperty()"      | "null"        | "null"
    }

    @Unroll
    def "warns when task field references an object of type #type"() {
        buildFile << """
            class SomeBean {
                private ${type} badReference
            }
            
            class SomeTask extends DefaultTask {
                private final ${type} badReference
                private final bean = new SomeBean()
                
                SomeTask() {
                    badReference = ${reference}
                    bean.badReference = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.reference = " + badReference
                    println "bean.reference = " + bean.badReference
                }
            }

            task other
            task broken(type: SomeTask)
        """

        when:
        instantRun "broken"

        then:
        outputContains("instant-execution > Cannot serialize object of type ${type} as these are not supported with instant execution.")

        when:
        instantRun "broken"

        then:
        outputContains("this.reference = null")
        outputContains("bean.reference = null")

        where:
        type          | reference
        Project.name  | "project"
        Gradle.name   | "project.gradle"
        Settings.name | "project.gradle.settings"
        Task.name     | "project.tasks.other"
    }

    def "task can reference itself"() {
        buildFile << """
            class SomeBean {
                private SomeTask owner
            }
            
            class SomeTask extends DefaultTask {
                private final SomeTask thisTask
                private final bean = new SomeBean()
                
                SomeTask() {
                    thisTask = this
                    bean.owner = this
                }

                @TaskAction
                void run() {
                    println "thisTask = " + (thisTask == this) 
                    println "bean.owner = " + (bean.owner == this)
                }
            }

            task ok(type: SomeTask)
        """

        when:
        instantRun "ok"
        instantRun "ok"

        then:
        outputContains("thisTask = true")
        outputContains("bean.owner = true")
    }
}
