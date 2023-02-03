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

import spock.lang.Ignore
import spock.lang.Issue

class DeferredTaskCreationIntegrationTest extends AbstractDeferredTaskDefinitionIntegrationTest{

    def "task is created and configured when included directly in task graph"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.register("task2", SomeTask) {
                println "Configure ${path}"
            }
            tasks.register("task3", SomeTask)
        '''

        when:
        run("task1")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        result.assertNotOutput(":task2")
        result.assertNotOutput(":task3")

        when:
        run("task2")

        then:
        outputContains("Create :task2")
        outputContains("Configure :task2")
        result.assertNotOutput(":task1")
        result.assertNotOutput(":task3")

        when:
        run("task3")

        then:
        outputContains("Create :task3")
        result.assertNotOutput(":task1")
        result.assertNotOutput(":task2")
    }

    def "task is created and configured when referenced as a task dependency"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.register("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn task1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task2")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured when referenced as task dependency via task provider"() {
        buildFile << '''
            def t1 = tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.create("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn t1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")

        when:
        run("task2")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured when referenced during configuration"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            // Eager
            tasks.create("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn task1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured eagerly when referenced using withType(type, action)"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.register("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.create("other")
            tasks.withType(SomeTask) {
                println "Matched ${path}"
            }
        '''

        when:
        run("other")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Matched :task1")
        result.assertNotOutput("task2")
    }

    @Issue("https://github.com/gradle/gradle-native/issues/707")
    def "task is created and configured eagerly when referenced using all { action }"() {
        buildFile << """
            tasks.register("task1", SomeTask) {
                println "Configure \${path}"
            }

            tasks.all {
                println "Action " + path
            }
        """

        expect:
        succeeds("help")
        result.output.count("Create :task1") == 1
        result.output.count("Configure :task1") == 1
        result.output.count("Configure :") == 1
        result.output.count("Action :task1") == 1
        result.output.count("Action :") == 13 || 15 // built in tasks + task1 (reduced distribution has only 12 built in tasks)
    }

    @Issue("https://github.com/gradle/gradle/issues/5148")
    def "can get a task by name with a filtered collection"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }

            tasks.create("other") {
                dependsOn tasks.withType(SomeTask).getByName("task1")
            }
        '''

        when:
        run "other"

        then:
        outputContains("Create :task1")
    }

    def "can construct a custom task with constructor arguments"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.register('myTask', CustomTask, 'hello', 42)"

        when:
        run 'myTask'

        then:
        outputContains("hello 42")
    }

    def "can construct a task with @Inject services"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor

            class CustomTask extends DefaultTask {
                private final WorkerExecutor executor

                @Inject
                CustomTask(WorkerExecutor executor) {
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it" : "NOT IT")
                }
            }

            tasks.register('myTask', CustomTask)
        """

        when:
        run 'myTask'

        then:
        outputContains("got it")
    }

    def "can construct a task with @Inject services and constructor args"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor

            class CustomTask extends DefaultTask {
                private final int number
                private final WorkerExecutor executor

                @Inject
                CustomTask(int number, WorkerExecutor executor) {
                    this.number = number
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it \$number" : "\$number NOT IT")
                }
            }

            tasks.register('myTask', CustomTask, 15)
        """

        when:
        run 'myTask'

        then:
        outputContains("got it 15")
    }

    @Ignore
    @Issue("https://github.com/gradle/gradle/issues/6558")
    def "can register tasks in multi-project build that iterates over allprojects and tasks in task action"() {
        settingsFile << """
            include 'a', 'b', 'c', 'd'
        """
        buildFile << """
            class MyTask extends DefaultTask {
                @TaskAction
                void doIt() {
                    for (Project subproject : project.rootProject.getAllprojects()) {
                        for (MyTask myTask : subproject.tasks.withType(MyTask)) {
                            println "Looking at " + myTask.path
                        }
                    }
                }
            }

            allprojects {
                (1..10).each {
                    def mytask = tasks.register("mytask" + it, MyTask)
                }
            }
        """
        expect:
        succeeds("mytask0", "--parallel")
    }

    @Issue("https://github.com/gradle/gradle-native/issues/814")
    def "can locate task by name and type with named"() {
        buildFile << """
            class CustomTask extends DefaultTask {
                @Internal
                String message
                @Internal
                int number

                @TaskAction
                void print() {
                    println message + " " + number
                }
            }
            task foo(type: CustomTask)
            tasks.register("bar", CustomTask)

            tasks.named("foo", CustomTask).configure {
                message = "foo named(String, Class)"
            }
            tasks.named("foo", CustomTask) {
                number = 100
            }
            tasks.named("foo") {
                number = number * 2
            }
            tasks.named("bar", CustomTask) {
                message = "bar named(String, Class, Action)"
            }
            tasks.named("bar", CustomTask).configure {
                number = 12345
            }
        """
        expect:
        succeeds("foo", "bar")
        outputContains("foo named(String, Class) 200")
        outputContains("bar named(String, Class, Action) 12345")
    }
}
