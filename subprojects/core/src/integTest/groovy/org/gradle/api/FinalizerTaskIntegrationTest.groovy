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
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.*

class FinalizerTaskIntegrationTest extends AbstractIntegrationSpec {
    void 'finalizer tasks are scheduled as expected'() {
        setupProject()

        when:
        succeeds(*requestedTasks)

        then:
        result.assertTasksExecutedInOrder any(':d', exact(':c', ':a')), ':b'

        where:
        requestedTasks << [ ['a'], ['a', 'b'], ['d', 'a'] ]
    }

    @Unroll
    void 'finalizer tasks work with task excluding'() {
        setupProject()
        executer.withArguments('-x', excludedTask)

        tasksNotInGraph.each { task ->
            buildFile << """
                gradle.taskGraph.whenReady { graph ->
                    assert !graph.hasTask('$task')
                }
            """
        }

        when:
        succeeds 'a'

        then:
        result.assertTasksExecutedInOrder(expectedExecutedTasks as Object[])

        where:
        excludedTask | expectedExecutedTasks
        'b'          | [':c', ':a']
        'd'          | [':c', ':a', ':b']
        'a'          | []


        tasksNotInGraph = [':a', ':b', ':c', ':d'] - expectedExecutedTasks
    }

    @Unroll
    void 'finalizer tasks work with --continue'() {
        setupProject()
        executer.withArguments('--continue')

        buildFile << """
            ${failingTask}.doLast { throw new RuntimeException() }
        """

        when:
        fails(*requestedTasks)

        then:
        result.assertTasksExecutedInOrder(expectedExecutedTasks as Object[])

        where:
        requestedTasks | failingTask | expectedExecutedTasks
        ['a']          | 'c'         | [':c']
        ['a', 'b']     | 'a'         | [any(':d', exact(':c', ':a')), ':b']
        ['a', 'b']     | 'c'         | [':c', ':d', ':b']
    }

    @Ignore
    @Unroll
    void 'finalizer tasks work with task disabling'() {
        setupProject()
        buildFile << """
            $taskDisablingStatement

            gradle.taskGraph.whenReady { graph ->
                assert [a, b, c, d].every { graph.hasTask(it) }
            }
        """

        when:
        succeeds 'a'

        then:
        executedTasks == [':c']

        where:
        taskDisablingStatement << ['a.enabled = false', 'a.onlyIf {false}']
    }

    @Ignore
    void 'requesting to run finalizer task before finalized results in a circular dependency failure'() {
        setupProject()

        expect:
        fails 'b', 'a'
    }

    void 'finalizer tasks are executed as expected in parallel builds'() {
        setupMultipleProjects()
        executer.withArguments('--parallel')

        when:
        succeeds 'a'

        then:
        result.assertTasksExecutedInOrder(any(':b:d', exact(':a:c', ':a:a')), ':b:b')
    }

    void 'finalizers for finalizers are executed when finalized is executed'() {
        buildFile << """
            task a {
                finalizedBy 'b'
            }
            task b {
                finalizedBy 'c'
            }
            task c
        """

        when:
        succeeds 'a'

        then:
        result.assertTasksExecutedInOrder ':a', ':b', ':c'
    }

    void 'finalizer tasks are executed after their dependencies'() {
        buildFile << """
            task a {
                dependsOn 'b', 'c'
            }
            task b
            task c {
                finalizedBy 'b'
            }
        """

        when:
        succeeds 'a'

        then:
        result.assertTasksExecutedInOrder ':c', ':b', ':a'
    }

    void 'circular dependency errors are detected for finalizer tasks'() {
        buildFile << """
            task a {
                finalizedBy 'b'
                dependsOn 'c'
            }
            task b
            task c {
                mustRunAfter 'b'
            }
        """

        when:
        fails 'a'

        then:
        failure.assertHasDescription """Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)"""
    }

    void 'finalizer task can be used by multiple tasks that depend on one another'(){
        buildFile << """
            task a {
                finalizedBy 'c'
            }
            task b {
                dependsOn 'a'
                finalizedBy 'c'
            }
            task c
        """

        when:
        succeeds 'b'

        then:
        result.assertTasksExecutedInOrder ':a', ':b', ':c'
    }

    private void setupProject() {
        buildFile << """
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
