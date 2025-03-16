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

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

class DeferredTaskConfigurationIntegrationTest extends AbstractDeferredTaskDefinitionIntegrationTest {
    def "build logic can configure each task only when required"() {
        buildFile << '''
            tasks.register("task1", SomeTask).configure {
                println "Configure ${path}"
            }
            tasks.named("task1").configure {
                println "Configure again ${path}"
            }
            tasks.register("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.register("task3")
            tasks.configureEach {
                println "Received ${path}"
            }
            tasks.create("other") {
                dependsOn "task3"
            }
        '''

        when:
        run("other")

        then:
        outputContains("Received :other")
        outputContains("Received :task3")
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task1")

        then:
        outputContains("Received :other")
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Configure again :task1")
        outputContains("Received :task1")
        result.assertNotOutput("task2")
        result.assertNotOutput("task3")
    }

    def "build logic can configure each task of a given type only when required"() {
        buildFile << '''
            tasks.register("task1", SomeTask).configure {
                println "Configure ${path}"
            }
            tasks.named("task1").configure {
                println "Configure again ${path}"
            }
            tasks.register("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.register("task3", SomeOtherTask)

            tasks.withType(SomeTask).configureEach {
                println "Received ${path}"
            }
            tasks.create("other") {
                dependsOn "task3"
            }
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("Received :")
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task1")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Configure again :task1")
        outputContains("Received :task1")
        result.assertNotOutput("task2")
    }

    @Issue("https://github.com/gradle/gradle-native/issues/661")
    def "executes each configuration actions once when realizing a task"() {
        buildFile << '''
            def actionExecutionCount = [:].withDefault { 0 }

            class A extends DefaultTask {}

            tasks.withType(A).configureEach {
                actionExecutionCount.a1++
            }

            tasks.withType(A).configureEach {
                actionExecutionCount.a2++
            }

            def a = tasks.register("a", A) {
                actionExecutionCount.a3++
            }

            a.configure {
                actionExecutionCount.a4++
            }

            tasks.withType(A).configureEach {
                actionExecutionCount.a5++
            }

            a.configure {
                actionExecutionCount.a6++
            }

            task assertActionExecutionCount {
                dependsOn a
                doLast {
                    assert actionExecutionCount.size() == 6
                    assert actionExecutionCount.values().every { it == 1 }
                }
            }
        '''

        expect:
        succeeds 'assertActionExecutionCount'
    }

    @Issue("https://github.com/gradle/gradle-native/issues/662")
    def "runs the lazy configuration actions in the same order as the eager configuration actions"() {
        buildFile << '''
            def actionExecutionOrderForTaskA = []

            class A extends DefaultTask {}

            tasks.withType(A).configureEach {
                actionExecutionOrderForTaskA << "1"
            }

            tasks.withType(A).configureEach {
                actionExecutionOrderForTaskA << "2"
            }

            def a = tasks.register("a", A) {
                actionExecutionOrderForTaskA << "3"
            }

            a.configure {
                actionExecutionOrderForTaskA << "4"
            }

            tasks.withType(A).configureEach {
                actionExecutionOrderForTaskA << "5"
            }

            a.configure {
                actionExecutionOrderForTaskA << "6"
            }

            def actionExecutionOrderForTaskB = []

            class B extends DefaultTask {}

            tasks.withType(B) {
                actionExecutionOrderForTaskB << "1"
            }

            tasks.withType(B) {
                actionExecutionOrderForTaskB << "2"
            }

            def b = tasks.create("b", B) {
                actionExecutionOrderForTaskB << "3"
            }

            b.configure {
                actionExecutionOrderForTaskB << "4"
            }

            tasks.withType(B) {
                actionExecutionOrderForTaskB << "5"
            }

            b.configure {
                actionExecutionOrderForTaskB << "6"
            }

            task assertActionExecutionOrder {
                dependsOn a, b
                doLast {
                    assert actionExecutionOrderForTaskA.size() == 6
                    assert actionExecutionOrderForTaskA == actionExecutionOrderForTaskB
                }
            }
        '''

        expect:
        succeeds 'assertActionExecutionOrder'
    }

    def "can execute #description during task creation action execution"() {
        createDirs("nested")
        settingsFile << "include 'nested'"
        buildFile << """
            tasks.create("foo") {
                ${code}
            }
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    def "can execute #description during task configuration action execution"() {
        createDirs("nested")
        settingsFile << "include 'nested'"
        buildFile << """
            tasks.create("foo")
            tasks.getByName("foo") {
                ${code}
            }
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Requires(
        value = IntegTestPreconditions.NotIsolatedProjects,
        reason = "Exercises IP incompatible behavior"
    )
    def "can execute #description on another project during task creation action execution"() {
        createDirs("nested", "other")
        settingsFile << "include 'nested', 'other'"
        buildFile << """
            project(":other") {
                tasks.create("foo") {
                    rootProject.${code}
                }
            }
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    def "realizes only the task of the given type when depending on a filtered task collection"() {
        buildFile << '''
            def defaultTaskRealizedCount = 0
            (1..100).each {
                tasks.register("aDefaultTask_$it") {
                    defaultTaskRealizedCount++
                }
            }
            def zipTaskRealizedCount = 0
            tasks.register("aZipTask", Zip) {
                destinationDirectory = buildDir
                zipTaskRealizedCount++
            }

            task foo {
                dependsOn tasks.withType(Zip)
                doLast {
                    assert defaultTaskRealizedCount == 0, "All DefaultTask shouldn't be realized"
                    assert zipTaskRealizedCount == 1, "All Zip task should be realized"
                }
            }
        '''

        expect:
        succeeds "foo"
    }

    def "realizes only the task of the given type when verifying if a filtered task collection is empty"() {
        buildFile << '''
            def defaultTaskRealizedCount = 0
            (1..100).each {
                tasks.register("aDefaultTask_$it") {
                    defaultTaskRealizedCount++
                }
            }
            def zipTaskRealizedCount = 0
            tasks.register("aZipTask", Zip) {
                zipTaskRealizedCount++
            }

            task foo {
                def hasZipTask = tasks.withType(Zip).empty
                doLast {
                    assert defaultTaskRealizedCount == 0, "All DefaultTask shouldn't be realized"
                    assert zipTaskRealizedCount == 1, "All Zip task should be realized"
                }
            }
        '''

        expect:
        succeeds "foo"
    }

    def "can realize a task provider inside a configureEach action"() {
        buildFile << """
            def foo = tasks.create("foo", SomeTask)
            def bar = tasks.register("bar") { println "Create :bar" }
            def baz = tasks.create("baz", SomeTask)
            def fizz = tasks.create("fizz", SomeTask)
            def fuzz = tasks.create("fuzz", SomeTask)

            tasks.withType(SomeTask).configureEach { task ->
                println "Configuring " + task.name
                bar.get()
            }

            task some { dependsOn tasks.withType(SomeTask) }
        """

        expect:
        succeeds("some")

        and:
        executed ":foo", ":baz", ":fizz", ":fuzz", ":some"
    }

    def "can lookup task created by rules"() {
        buildFile << """
            tasks.addRule("create some tasks") { taskName ->
                if (taskName == "bar") {
                    tasks.register("bar")
                } else if (taskName == "baz") {
                    tasks.create("baz")
                } else if (taskName == "notByRule") {
                    tasks.register("notByRule") {
                        throw new Exception("This should not be called")
                    }
                }
            }
            tasks.register("notByRule")

            task foo {
                dependsOn tasks.named("bar")
                dependsOn tasks.named("baz")
                dependsOn "notByRule"
            }

        """
        expect:
        succeeds("foo")
        result.assertTasksExecuted(":notByRule", ":bar", ":baz", ":foo")
    }

    @Requires(
        value = IntegTestPreconditions.NotIsolatedProjects,
        reason = "Exercises IP incompatible behavior"
    )
    def "can execute #description on another project during task configuration action execution"() {
        createDirs("nested", "other")
        settingsFile << "include 'nested', 'other'"
        buildFile << """
            project(":other") {
                tasks.create("foo")
                tasks.getByName("foo") {
                    rootProject.${code}
                }
            }
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    def "can execute #description during eager configuration action with registered task"() {
        buildFile << """
            tasks.withType(SomeTask) {
                ${code}
            }
            tasks.register("foo", SomeTask)
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Issue("https://github.com/gradle/gradle/issues/6319")
    @Requires(
        value = IntegTestPreconditions.NotIsolatedProjects,
        reason = "getTasksByName is not IP compatible"
    )
    def "can use getTasksByName from a lazy configuration action"() {
        createDirs("sub")
        settingsFile << """
            include "sub"
        """
        buildFile << """
            plugins {
                id 'base'
            }
            tasks.whenTaskAdded {
                // force realization of all tasks
            }
            tasks.register("foo") {
                dependsOn(project.getTasksByName("clean", true))
            }
        """
        file("sub/build.gradle") << """
            plugins {
                id 'base'
            }
            afterEvaluate {
                tasks.register("foo")
            }
        """
        expect:
        succeeds("help")
    }
}
