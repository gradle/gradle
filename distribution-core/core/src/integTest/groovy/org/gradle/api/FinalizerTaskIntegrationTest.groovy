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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.ToBeImplemented
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.any
import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.exact

class FinalizerTaskIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    void 'finalizer tasks are scheduled as expected (#requestedTasks)'() {
        given:
        setupProject()

        expect:
        2.times {
            succeeds(*requestedTasks)
            result.assertTasksExecutedInOrder any(':d', exact(':c', ':a')), ':b'
        }

        where:
        requestedTasks << [['a'], ['a', 'b'], ['d', 'a']]
    }

    @Unroll
    void 'finalizer tasks work with task excluding (#excludedTask)'() {
        setupProject()
        executer.beforeExecute {
            withArguments('-x', excludedTask)
        }

        tasksNotInGraph.each { task ->
            buildFile << """
                gradle.taskGraph.whenReady { graph ->
                    assert !graph.hasTask('$task')
                }
            """
        }

        expect:
        2.times {
            succeeds 'a'
            result.assertTasksExecutedInOrder(expectedExecutedTasks as Object[])
        }

        where:
        excludedTask | expectedExecutedTasks
        'b'          | [':c', ':a']
        'd'          | [':c', ':a', ':b']
        'a'          | []


        tasksNotInGraph = [':a', ':b', ':c', ':d'] - expectedExecutedTasks
    }

    @Unroll
    void 'finalizer tasks work with --continue (#requestedTasks, #failingTask)'() {
        setupProject()
        executer.beforeExecute {
            withArguments('--continue')
        }

        buildFile << """
            ${failingTask}.doLast { throw new RuntimeException() }
        """

        expect:
        2.times {
            fails(*requestedTasks)
            result.assertTasksExecutedInOrder(expectedExecutedTasks as Object[])
        }

        where:
        requestedTasks | failingTask | expectedExecutedTasks
        ['a']          | 'c'         | [':c']
        ['a', 'b']     | 'a'         | [any(':d', exact(':c', ':a')), ':b']
        ['a', 'b']     | 'c'         | [any(':c', ':d'), ':b'] // :c and :d might run in parallel with the configuration cache
    }

    @Ignore
    @Unroll
    void 'finalizer tasks work with task disabling (#taskDisablingStatement)'() {
        setupProject()
        buildFile << """
            $taskDisablingStatement

            gradle.taskGraph.whenReady { graph ->
                assert [a, b, c, d].every { graph.hasTask(it) }
            }
        """

        expect:
        2.times {
            succeeds 'a'
            result.assertTasksExecuted(':c')
        }

        where:
        taskDisablingStatement << ['a.enabled = false', 'a.onlyIf {false}']
    }

    @Ignore
    void 'requesting to run finalizer task before finalized results in a circular dependency failure'() {
        setupProject()

        expect:
        2.times {
            fails 'b', 'a'
        }
    }

    void 'finalizer tasks are executed as expected in parallel builds'() {
        setupMultipleProjects()
        executer.beforeExecute {
            withArguments('--parallel')
        }

        expect:
        2.times {
            succeeds 'a'
            result.assertTasksExecutedInOrder(any(':b:d', exact(':a:c', ':a:a')), ':b:b')
        }
    }

    void 'finalizers for finalizers are executed when finalized is executed'() {
        buildFile """
            task a {
                finalizedBy 'b'
            }
            task b {
                finalizedBy 'c'
            }
            task c
        """

        expect:
        2.times {
            succeeds 'a'
            result.assertTasksExecutedInOrder ':a', ':b', ':c'
        }
    }

    void 'finalizer tasks are executed after their dependencies'() {
        buildFile """
            task a {
                dependsOn 'b', 'c'
            }
            task b
            task c {
                finalizedBy 'b'
            }
        """

        expect:
        2.times {
            succeeds 'a'
            result.assertTasksExecutedInOrder ':c', ':b', ':a'
        }
    }

    void 'circular dependency errors are detected for finalizer tasks'() {
        buildFile """
            task a {
                finalizedBy 'b'
                dependsOn 'c'
            }
            task b
            task c {
                mustRunAfter 'b'
            }
        """

        expect:
        2.times {
            fails 'a'
            failure.assertHasDescription """Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)"""
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/2293")
    void 'circular dependency is detected on cycle within chained finalizers'() {
        buildFile """
            task a {
                finalizedBy 'b'
            }
            task b {
                finalizedBy 'c'
            }
            task c {
                finalizedBy 'c'
            }
        """

        expect:
        2.times {
            fails 'a'
            failure.assertHasDescription """Circular dependency between the following tasks:
:c
\\--- :c (*)

(*) - details omitted (listed previously)"""
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/2293")
    def "circular dependency detected with complex finalizedBy cycle in the graph"() {
        buildFile """
            task a
            task b
            task c
            task d
            task e
            task f

            a.dependsOn b
            b.dependsOn c
            b.finalizedBy d
            d.dependsOn f
            e.dependsOn d
            f.dependsOn e
        """

        expect:
        2.times {
            fails 'a'
            failure.assertHasDescription """Circular dependency between the following tasks:
:d
\\--- :f
     \\--- :e
          \\--- :d (*)

(*) - details omitted (listed previously)"""
        }
    }


    void 'finalizer task can be used by multiple tasks that depend on one another'() {
        buildFile """
            task a {
                finalizedBy 'c'
            }
            task b {
                dependsOn 'a'
                finalizedBy 'c'
            }
            task c
        """

        expect:
        2.times {
            succeeds 'b'
            result.assertTasksExecutedInOrder ':a', ':b', ':c'
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/5415")
    void 'finalizers are executed after the last task to be finalized'() {
        settingsFile << """
            include "a"
            include "b"
        """
        buildFile """
            configure(project(':a')) {
                task finalizer {
                    doLast {
                        sleep 100
                    }
                }

                task foo {
                    finalizedBy finalizer
                    doLast {
                        sleep 500
                    }
                }
            }

            configure(project(':b')) {
                task foo {
                    finalizedBy ':a:finalizer'
                    doLast {
                        sleep 1000
                    }
                }
            }
        """

        expect:
        2.times {
            run "foo", "--parallel"
            result.assertTaskOrder(any(":a:foo", ":b:foo"), ":a:finalizer")
        }
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/10549")
    def "mustRunAfter is respected for finalizer without direct dependency"() {
        settingsFile << """
            include 'a'
            include 'b'
        """
        buildFile """
            configure(project(':a')) {
                task finalizer {
                    doLast {
                        println "finalized"
                    }
                }

                task work {
                    doLast {
                        sleep 1000
                        println "executed \${path}"
                    }
                    finalizedBy(finalizer)
                }
            }

            configure(project(':b')) {
                task work {
                    doLast {
                        println "executed \${path}"
                    }
                    mustRunAfter(":a:finalizer")
                }
            }
        """

        expect:
        2.times {
            run("work", "--parallel")
            // TODO: Should be:
            // result.assertTaskOrder(":a:work", ":a:finalizer", ":b:work")
            result.assertTaskOrder(any(exact(":a:work", ":a:finalizer"), ":b:work"))
        }

        and: "Apply workaround"
        buildFile """
            configure(project(':b')) {
                work.mustRunAfter(":a:work")
            }
        """
        2.times {
            run("work", "--parallel")
            result.assertTaskOrder(":a:work", ":a:finalizer", ":b:work")
        }
    }

    private void setupProject() {
        buildFile """
            class NotParallel extends DefaultTask {}

            task a {
                finalizedBy 'b'
                dependsOn 'c'
            }
            task b {
                dependsOn 'd'
            }
            task c(type: NotParallel)
            task d(type: NotParallel)
        """
    }

    private void setupMultipleProjects() {
        settingsFile << """
            include 'a', 'b'
        """

        file('a/build.gradle') << """
            task a {
                finalizedBy ':b:b'
                dependsOn 'c'
            }
            task c
        """

        file('b/build.gradle') << """
            task b {
                dependsOn 'd'
            }
            task d
        """
    }
}
