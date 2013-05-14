package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil
import spock.lang.Ignore
import spock.lang.Unroll

class FinalizerTaskIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    void 'finalizer tasks are scheduled as expected'() {
        setupProject()

        when:
        succeeds(*requestedTasks)

        then:
        executedTasks == expectedExecutedTasks

        where:
        requestedTasks | expectedExecutedTasks
        ['a']          | [':c', ':a', ':d', ':b']
        ['a', 'b']     | [':c', ':a', ':d', ':b']
        ['d', 'a']     | [':d', ':c', ':a', ':b']
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
        executedTasks == expectedExecutedTasks

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
        executedTasks == expectedExecutedTasks

        where:
        requestedTasks | failingTask | expectedExecutedTasks
        ['a']          | 'c'         | [':c']
        ['a', 'b']     | 'a'         | [':c', ':a', ':d', ':b']
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
        executedTasks == [':a:c', ':a:a', ':b:d', ':b:b']
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
        executedTasks == [':a', ':b', ':c']
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
        executedTasks == [':c', ':b', ':a']
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
        failure.assertHasDescription TextUtil.toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)""")
    }

    private void setupProject() {
        buildFile << """
            task a {
                finalizedBy 'b'
                dependsOn 'c'
            }
            task b {
                dependsOn 'd'
            }
            task c
            task d
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
