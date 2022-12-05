/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import spock.lang.Issue
import spock.lang.Timeout

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.any
import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.exact
import static org.hamcrest.CoreMatchers.startsWith

class TaskExecutionIntegrationTest extends AbstractIntegrationSpec implements TasksWithInputsAndOutputs {

    @UnsupportedWithConfigurationCache
    def taskCanAccessTaskGraph() {
        buildFile << """
            boolean notified = false
            task a(dependsOn: 'b') {
                doLast { task ->
                    assert notified
                    assert gradle.taskGraph.hasTask(task)
                    assert gradle.taskGraph.hasTask(':a')
                    assert gradle.taskGraph.hasTask(a)
                    assert gradle.taskGraph.hasTask(':b')
                    assert gradle.taskGraph.hasTask(b)
                    assert gradle.taskGraph.allTasks == [b, a]
                }
            }
            task b
            gradle.taskGraph.whenReady { graph ->
                assert graph.hasTask(':a')
                assert graph.hasTask(a)
                assert graph.hasTask(':b')
                assert graph.hasTask(b)
                assert graph.allTasks == [b, a]
                notified = true
            }
        """
        expect:
        2.times {
            succeeds "a"
            result.assertTasksExecuted(":b", ":a")
        }
    }

    @UnsupportedWithConfigurationCache
    def buildFinishedHookCanAccessTaskGraph() {
        buildFile << """
            task a(dependsOn: 'b')
            task b
            gradle.buildFinished {
                def graph = gradle.taskGraph
                // This is existing behaviour, not desired behaviour
                assert !graph.hasTask(':a')
                assert !graph.hasTask(a)
                assert !graph.hasTask(':b')
                assert !graph.hasTask(b)
                assert graph.allTasks == [b, a]
            }
        """
        expect:
        2.times {
            succeeds "a"
            result.assertTasksExecuted(":b", ":a")
        }
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def executesAllTasksInASingleBuildAndEachTaskAtMostOnce() {
        buildFile << """
            gradle.taskGraph.whenReady { assert !project.hasProperty('graphReady'); ext.graphReady = true }
            task a {
                doLast { task ->
                    project.ext.executedA = task
                }
            }
            task b {
                doLast {
                    assert a == project.executedA
                    assert gradle.taskGraph.hasTask(':a')
                }
            }
            task c(dependsOn: a)
            task d(dependsOn: a)
            task e(dependsOn: [a, d]);
        """
        expect:
        2.times {
            run("a", "b").assertTasksExecuted(":a", ":b")
            run("a", "a").assertTasksExecuted(":a")
            run("c", "a").assertTasksExecuted(":a", ":c")
            run("c", "e").assertTasksExecuted(":a", ":c", ":d", ":e")
        }
    }

    def executesMultiProjectsTasksInASingleBuildAndEachTaskAtMostOnce() {
        settingsFile << "include 'child1', 'child2', 'child1-2', 'child1-2-2'"
        buildFile << """
            task a
            allprojects {
                task b
                task c(dependsOn: ['b', ':a'])
            };
        """

        expect:
        2.times {
            run("a", "c").assertTasksExecuted(":a", ":b", ":c", ":child1:b", ":child1:c", ":child1-2:b", ":child1-2:c", ":child1-2-2:b", ":child1-2-2:c", ":child2:b", ":child2:c")
            run("b", ":child2:c").assertTasksExecuted(":b", ":child1:b", ":child1-2:b", ":child1-2-2:b", ":child2:b", ":a", ":child2:c")
        }
    }

    def executesMultiProjectDefaultTasksInASingleBuildAndEachTaskAtMostOnce() {
        settingsFile << "include 'child1', 'child2'"
        buildFile << """
            defaultTasks 'a', 'b'
            task a
            subprojects {
                task a(dependsOn: ':a')
                task b(dependsOn: ':a')
            }
        """

        expect:
        2.times {
            succeeds().assertTasksExecuted(":a", ":child1:a", ":child2:a", ":child1:b", ":child2:b")
        }
    }

    def doesNotExecuteTaskActionsWhenDryRunSpecified() {
        buildFile << """
            task a { doLast { fail() } }
            task b(dependsOn: a) { doLast { fail() } }
            defaultTasks 'b'
        """

        expect:
        2.times {
            // project defaults
            executer.withArguments("-m").run().normalizedOutput.contains(":a SKIPPED\n:b SKIPPED")
            // named tasks
            executer.withArguments("-m").withTasks("b").run().normalizedOutput.contains(":a SKIPPED\n:b SKIPPED")
        }
    }

    def executesTaskActionsInCorrectEnvironment() {
        buildFile << """
            // An action attached to built-in task
            task a { doLast { assert Thread.currentThread().contextClassLoader == getClass().classLoader } }

            // An action defined by a custom task
            task b(type: CustomTask)
            class CustomTask extends DefaultTask {
                @TaskAction def go() {
                    assert Thread.currentThread().contextClassLoader == getClass().classLoader
                }
            }

            // An action implementation
            task c
            class DoLast implements Action<Task> {
                void execute(Task t) {
                    assert Thread.currentThread().contextClassLoader == getClass().classLoader
                }
            }
            c.doLast new DoLast()

            // The following is NOT compatible with the configuration cache because anonymous inner classes
            // in a groovy script always capture the script object reference:
            //   c.doLast new Action<Task>() {
            //       void execute(Task t) {
            //           assert Thread.currentThread().contextClassLoader == getClass().classLoader
            //       }
            //   }
        """
        expect:
        2.times {
            succeeds("a", "b", "c")
        }
    }

    def excludesTasksWhenExcludePatternSpecified() {
        settingsFile << """
            include 'sub'
            rootProject.name = 'root'
        """
        buildFile << """
            task a
            task b(dependsOn: a)
            task c(dependsOn: [a, b])
            task d(dependsOn: c)
            defaultTasks 'd'
        """
        file("sub/build.gradle") << """
            task c
            task d(dependsOn: c)
        """

        expect:
        2.times {
            // Exclude entire branch
            executer.withTasks(":d").withArguments("-x", "c").run().assertTasksExecuted(":d")
            // Exclude direct dependency
            executer.withTasks(":d").withArguments("-x", "b").run().assertTasksExecuted(":a", ":c", ":d")
            // Exclude using paths and multi-project
            executer.withTasks("d").withArguments("-x", "c").run().assertTasksExecuted(":d", ":sub:d")
            executer.withTasks("d").withArguments("-x", "sub:c").run().assertTasksExecuted(":a", ":b", ":c", ":d", ":sub:d")
            executer.withTasks("d").withArguments("-x", ":sub:c").run().assertTasksExecuted(":a", ":b", ":c", ":d", ":sub:d")
            executer.withTasks("d").withArguments("-x", "d").run().assertTasksExecuted()
            // Project defaults
            executer.withArguments("-x", "b").run().assertTasksExecuted(":a", ":c", ":d", ":sub:c", ":sub:d")
            // Unknown task
            executer.withTasks("d").withArguments("-x", "unknown").runWithFailure().assertThatDescription(startsWith("Task 'unknown' not found in root project 'root' and its subprojects."))
        }
    }

    def "unqualified exclude task name does not exclude tasks from parent projects"() {
        settingsFile << "include 'sub'"
        buildFile << """
            task a
        """
        file("sub/build.gradle") << """
            task a
            task b
            task c(dependsOn: [a, b, ':a'])
        """

        expect:
        2.times {
            executer.inDirectory(file('sub')).withTasks('c').withArguments('-x', 'a').run().assertTasksExecuted(':a', ':sub:b', ':sub:c')
        }
    }

    def 'can use camel-case matching to exclude tasks'() {
        buildFile << """
            task someDep
            task someOtherDep
            task someTask(dependsOn: [someDep, someOtherDep])
        """

        expect:
        2.times {
            executer.withTasks("someTask").withArguments("-x", "sODep").run().assertTasksExecuted(":someDep", ":someTask")
            executer.withTasks("someTask").withArguments("-x", ":sODep").run().assertTasksExecuted(":someDep", ":someTask")
        }
    }

    def 'can combine exclude task filters'() {
        buildFile << """
            task someDep
            task someOtherDep
            task someTask(dependsOn: [someDep, someOtherDep])
        """

        expect:
        2.times {
            executer.withTasks("someTask").withArguments("-x", "someDep", "-x", "someOtherDep").run().assertTasksExecuted(":someTask")
            executer.withTasks("someTask").withArguments("-x", ":someDep", "-x", ":someOtherDep").run().assertTasksExecuted(":someTask")
            executer.withTasks("someTask").withArguments("-x", "sODep", "-x", "soDep").run().assertTasksExecuted(":someTask")
        }
    }

    @Issue(["https://issues.gradle.org/browse/GRADLE-3031", "https://issues.gradle.org/browse/GRADLE-2974"])
    def 'excluding a task that is a dependency of multiple tasks'() {
        settingsFile << "include 'sub'"
        buildFile << """
            task a
            task b(dependsOn: a)
            task c(dependsOn: a)
            task d(dependsOn: [b, c])
        """
        file("sub/build.gradle") << """
            task a
        """

        expect:
        2.times {
            executer.withTasks("d").withArguments("-x", "a").run().assertTasksExecuted(":b", ":c", ":d")
            executer.withTasks("b", "a").withArguments("-x", ":a").run().assertTasksExecuted(":b", ":sub:a")
        }
    }

    def "sensible error message for circular task dependency"() {
        buildFile << """
            task a(dependsOn: 'b')
            task b(dependsOn: 'a')
        """
        expect:
        2.times {
            fails 'b'
            failure.assertHasDescription """|Circular dependency between the following tasks:
                |:a
                |\\--- :b
                |     \\--- :a (*)
                |
                |(*) - details omitted (listed previously)""".stripMargin()
        }
    }

    def "honours mustRunAfter task ordering"() {
        buildFile << """
            task a {
                mustRunAfter 'b'
            }
            task b
            task c(dependsOn: ['a', 'b'])
            task d
            c.mustRunAfter d

        """
        expect:
        2.times {
            succeeds 'c', 'd'
            result.assertTasksExecutedInOrder(any(':d', ':b', ':a'), ':c')
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/21962")
    def "honours mustRunAfter task ordering declared via dependency resolution"() {
        settingsFile << """
            include('a')
            include('b')
        """
        taskTypeWithOutputFileProperty()
        buildFile << """
            subprojects {
                configurations.create("implementation") {
                    attributes.attribute(${Usage.name}.USAGE_ATTRIBUTE, objects.named(${Usage.name}, "test"))
                }
            }
            project('a') {
                task producer(type: FileProducer) {
                    output = layout.buildDirectory.file("out.txt")
                }
                artifacts { implementation producer.output }
            }
            project('b') {
                dependencies {
                    implementation project(':a')
                }
                task resolve {
                    mustRunAfter configurations.implementation
                    doLast {  }
                }
            }
        """
        expect:
        2.times {
            succeeds 'resolve', 'producer'
            result.assertTasksExecutedInOrder(':a:producer', ':b:resolve')
        }
    }

    def "finalizer task is executed if a finalized task is executed"() {
        buildFile << """
            task a
            task b {
                doLast {}
                finalizedBy a
            }
        """
        expect:
        2.times {
            succeeds 'b'
            executed(":a")
        }
    }

    def "finalizer task is executed even if the finalised task fails"() {
        buildFile << """
            task a
            task b  {
                doLast { throw new RuntimeException() }
                finalizedBy a
            }
        """
        expect:
        2.times {
            fails 'b'
            executed(":a")
        }
    }

    def "finalizer task is not executed if the finalized task does not run"() {
        buildFile << """
            task a {
                doLast { throw new RuntimeException() }
            }
            task b
            task c {
                doLast {}
                dependsOn a
                finalizedBy b
                onlyIf { false }
            }
        """
        expect:
        2.times {
            fails 'c'
            notExecuted(":b")
        }
    }

    def "sensible error message for circular task dependency due to mustRunAfter"() {
        buildFile << """
            task a {
                mustRunAfter 'b'
            }
            task b(dependsOn: 'a')
        """
        expect:
        2.times {
            fails 'b'
            failure.assertHasDescription """|Circular dependency between the following tasks:
                |:a
                |\\--- :b
                |     \\--- :a (*)
                |
                |(*) - details omitted (listed previously)""".stripMargin()
        }
    }

    def "checked exceptions thrown by tasks are reported correctly"() {
        buildFile << """
            class SomeTask extends DefaultTask {

                @TaskAction
                void explode() {
                    throw new Exception("I am the checked exception")
                }
            }

            task explode(type: SomeTask) {

            }
        """

        expect:
        2.times {
            fails "explode"
            failure.assertHasCause "java.lang.Exception: I am the checked exception"
        }
    }

    def "honours shouldRunAfter task ordering"() {
        buildFile << """
            task a() {
                dependsOn 'b'
            }
            task b() {
                shouldRunAfter 'c'
            }
            task c()
            task d() {
                dependsOn 'c'
            }
        """
        expect:
        2.times {
            args("--max-workers=1")
            succeeds 'a', 'd'
            result.assertTasksExecuted(':c', ':b', ':a', ':d')
        }
    }

    def "multiple should run after ordering can be ignored for one execution plan"() {
        buildFile << """
            task a() {
                dependsOn 'b', 'h'
            }
            task b() {
                dependsOn 'c'
            }
            task c() {
                dependsOn 'g'
                shouldRunAfter 'd'
            }
            task d() {
                finalizedBy 'e'
                dependsOn 'f'
            }
            task e()
            task f() {
                dependsOn 'c'
            }
            task g() {
                shouldRunAfter 'h'
            }
            task h() {
                dependsOn 'b'
            }
        """

        expect:
        2.times {
            args("--max-workers=1")
            succeeds 'a', 'd'
            result.assertTasksExecuted(':g', ':c', ':b', ':h', ':a', ':f', ':d', ':e')
        }
    }

    @Issue("GRADLE-3575")
    def "honours task ordering with finalizers on finalizers"() {
        buildFile << """
            task a() {
                dependsOn 'c', 'g'
            }

            task b() {
                dependsOn 'd'
                finalizedBy 'e'
            }

            task c() {
                dependsOn 'd'
            }

            task d() {
                dependsOn 'f'
            }

            task e() {
                finalizedBy 'h'
            }

            task f() {
                finalizedBy 'h'
            }

            task g() {
                dependsOn 'd'
            }

            task h()
        """

        expect:
        2.times {
            succeeds 'a'
            result.assertTasksExecutedInOrder(
                any(
                    exact(':f', ':h'),
                    exact(any(':c', ':g'), ':a'),
                    exact(':f', ':d', ':c')
                )
            )
        }

        and:
        2.times {
            succeeds 'b'
            result.assertTasksExecutedInOrder(
                any(
                    exact(':f', ':h'),
                    exact(':b', ':e'),
                    exact(':f', ':d', ':b')
                )
            )
        }

        and:
        2.times {
            succeeds 'a', 'b'
            result.assertTasksExecutedInOrder(
                any(
                    exact(':f', ':h'),
                    exact(':b', ':e'),
                    exact(':f', ':d', any(':b', ':c')),
                    exact(any(':c', ':g'), ':a'),
                )
            )
        }

        and:
        2.times {
            succeeds 'b', 'a'
            result.assertTasksExecutedInOrder(
                any(
                    exact(':f', ':h'),
                    exact(':b', ':e'),
                    exact(':f', ':d', any(':b', ':c')),
                    exact(any(':c', ':g'), ':a'),
                )
            )
        }
    }

    @Issue("gradle/gradle#783")
    def "executes finalizer task as soon as possible after finalized task"() {
        buildFile << """
            project(":a") {
                task jar() {
                  dependsOn "compileJava"
                }
                task compileJava() {
                  dependsOn ":b:jar"
                  finalizedBy "compileFinalizer"
                }
                task compileFinalizer()
            }

            project(":b") {
                task jar()
            }

            task build() {
              dependsOn ":a:jar"
              dependsOn ":b:jar"
            }
        """
        settingsFile << "include 'a', 'b'"

        expect:
        2.times {
            succeeds ':build'
            result.assertTasksExecutedInOrder ':b:jar', ':a:compileJava', any(':a:compileFinalizer', ':a:jar', ':build')
        }
    }

    @Issue(["gradle/gradle#769", "gradle/gradle#841"])
    def "execution succeed in presence of long dependency chain"() {
        def count = 9000
        buildFile << """
            task a() {
                finalizedBy 'f'
            }

            task f() {
                dependsOn "d_0"
            }

            def nextIndex
            ${count}.times {
                nextIndex = it + 1
                task "d_\$it"() { task ->
                    dependsOn "d_\$nextIndex"
                }
            }
            task "d_\$nextIndex"()
        """

        expect:
        2.times {
            succeeds 'a'
            result.assertTasksExecutedInOrder(([':a'] + (count..0).collect { ":d_$it" } + [':f']) as String[])
        }
    }

    def "produces a sensible error when a task declares both outputs and destroys"() {
        buildFile << """
            task a {
                outputs.file('foo')
                destroyables.register('bar')
            }
        """
        file('foo') << 'foo'
        file('bar') << 'bar'

        expect:
        2.times {
            fails 'a'
            failure.assertHasDescription('Task :a has both outputs and destroyables defined.  A task can define either outputs or destroyables, but not both.')
        }
    }

    def "produces a sensible error when a task declares both inputs and destroys"() {
        buildFile << """
            task a {
                inputs.file('foo')
                destroyables.register('bar')
            }
        """
        file('foo') << 'foo'
        file('bar') << 'bar'

        expect:
        2.times {
            fails 'a'
            failure.assertHasDescription('Task :a has both inputs and destroyables defined.  A task can define either inputs or destroyables, but not both.')
        }
    }

    def "produces a sensible error when a task declares both local state and destroys"() {
        buildFile << """
            task a {
                localState.register('foo')
                destroyables.register('bar')
            }
        """
        file('foo') << 'foo'
        file('bar') << 'bar'

        expect:
        2.times {
            fails 'a'
            failure.assertHasDescription('Task :a has both local state and destroyables defined.  A task can define either local state or destroyables, but not both.')
        }
    }

    @Timeout(30)
    def "downstream dependencies of a failed task do not block destroyer to run"() {
        buildFile << """
            def mutatedFile = file("build/mutated.txt")
            def destroyer = tasks.register("destroyer") {
                destroyables.register(mutatedFile)
                doLast {
                    assert mutatedFile.delete()
                }
            }
            def producer = tasks.register("producer") {
                outputs.file(mutatedFile)
                doLast {
                    mutatedFile.text = "created"
                }
            }
            def failingConsumer = tasks.register("failingConsumer") {
                dependsOn(producer)
                finalizedBy(destroyer)
                doLast {
                    assert false
                }
            }
            def consumer = tasks.register("consumer") {
                dependsOn(failingConsumer)
                dependsOn(producer)
            }
        """

        expect:
        2.times {
            fails "consumer"
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/2401")
    def "re-run task does not query inputs after execution"() {
        buildFile << """
            class CustomTask extends DefaultTask {
                private boolean executed

                @InputDirectory
                @Optional
                File getInputDirectory() {
                    if (!executed) {
                        return null
                    }
                    throw new NullPointerException("Busted")
                }

                @OutputFile File outputFile

                @TaskAction
                void doStuff() {
                    executed = true
                }
            }

            task custom(type: CustomTask) {
                outputFile = file("output.txt")
            }
        """

        expect:
        2.times {
            succeeds "custom", "--rerun-tasks"
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/2293")
    def "detects a cycle with a task that mustRunAfter itself as finalizer of another task"() {
        buildFile << """
            def finalizer = tasks.register("finalizer")
            tasks.named("finalizer").configure {
                mustRunAfter(finalizer)
            }
            task myTask {
                finalizedBy finalizer
            }
        """
        expect:
        2.times {
            fails("myTask")
            failure.assertHasDescription """|Circular dependency between the following tasks:
                |:finalizer
                |\\--- :finalizer (*)
                |
                |(*) - details omitted (listed previously)""".stripMargin()
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/2293")
    def "detects a cycle when task finalizes itself"() {
        buildFile << """
            task a {
                finalizedBy 'b'
            }
            task b {
                finalizedBy 'b'
            }
        """
        expect:
        2.times {
            fails("a")
            failure.assertHasDescription """|Circular dependency between the following tasks:
                |:b
                |\\--- :b (*)
                |
                |(*) - details omitted (listed previously)""".stripMargin()
        }
    }

    def "task disabled by #method is skipped"() {

        given:
        buildFile << """
            tasks.register('myTask') {
                $code
            }
        """

        expect:
        2.times {
            succeeds ':myTask'
            skipped ':myTask'
        }

        where:
        method                     | code
        'setting enabled to false' | 'enabled = false'
        'onlyIf'                   | 'onlyIf { false }'
    }
}
