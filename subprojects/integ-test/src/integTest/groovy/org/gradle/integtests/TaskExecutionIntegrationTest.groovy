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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore
import spock.lang.Issue

import static org.hamcrest.Matchers.startsWith

public class TaskExecutionIntegrationTest extends AbstractIntegrationSpec {

    def taskCanAccessTaskGraph() {
        buildFile << """
    boolean notified = false
    task a(dependsOn: 'b') << { task ->
        assert notified
        assert gradle.taskGraph.hasTask(task)
        assert gradle.taskGraph.hasTask(':a')
        assert gradle.taskGraph.hasTask(a)
        assert gradle.taskGraph.hasTask(':b')
        assert gradle.taskGraph.hasTask(b)
        assert gradle.taskGraph.allTasks.contains(task)
        assert gradle.taskGraph.allTasks.contains(tasks.getByName('b'))
    }
    task b
    gradle.taskGraph.whenReady { graph ->
        assert graph.hasTask(':a')
        assert graph.hasTask(a)
        assert graph.hasTask(':b')
        assert graph.hasTask(b)
        assert graph.allTasks.contains(a)
        assert graph.allTasks.contains(b)
        notified = true
    }
"""
        when:
        succeeds "a"

        then:
        result.assertTasksExecuted(":b", ":a");
    }

    def executesAllTasksInASingleBuildAndEachTaskAtMostOnce() {
        buildFile << """
    gradle.taskGraph.whenReady { assert !project.hasProperty('graphReady'); ext.graphReady = true }
    task a << { task -> project.ext.executedA = task }
    task b << { 
        assert a == project.executedA
        assert gradle.taskGraph.hasTask(':a')
    }
    task c(dependsOn: a)
    task d(dependsOn: a)
    task e(dependsOn: [a, d]);
"""
        expect:
        run("a", "b").assertTasksExecuted(":a", ":b");
        run("a", "a").assertTasksExecuted(":a");
        run("c", "a").assertTasksExecuted(":a", ":c");
        run("c", "e").assertTasksExecuted(":a", ":c", ":d", ":e");
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
        run("a", "c").assertTasksExecuted(":a", ":b", ":c", ":child1:b", ":child1:c", ":child1-2:b", ":child1-2:c", ":child1-2-2:b", ":child1-2-2:c", ":child2:b", ":child2:c");
        run("b", ":child2:c").assertTasksExecuted(":b", ":child1:b", ":child1-2:b", ":child1-2-2:b", ":child2:b", ":a", ":child2:c");
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
        run().assertTasksExecuted(":a", ":child1:a", ":child2:a", ":child1:b", ":child2:b");
    }

    def doesNotExecuteTaskActionsWhenDryRunSpecified() {
        buildFile << """
    task a << { fail() }
    task b(dependsOn: a) << { fail() }
    defaultTasks 'b'
"""

        expect:
        // project defaults
        executer.withArguments("-m").run().assertTasksExecuted(":a", ":b");
        // named tasks
        executer.withArguments("-m").withTasks("b").run().assertTasksExecuted(":a", ":b");
    }

    def executesTaskActionsInCorrectEnvironment() {
        buildFile << """
    // An action attached to built-in task
    task a << { assert Thread.currentThread().contextClassLoader == getClass().classLoader }

    // An action defined by a custom task
    task b(type: CustomTask)
    class CustomTask extends DefaultTask {
        @TaskAction def go() {
            assert Thread.currentThread().contextClassLoader == getClass().classLoader
        }
    }

    // An action implementation
    task c
    c.doLast new Action<Task>() {
        void execute(Task t) {
            assert Thread.currentThread().contextClassLoader == getClass().classLoader
        }
    }
"""
        expect:
        succeeds("a", "b", "c")
    }

    def excludesTasksWhenExcludePatternSpecified() {
        settingsFile << "include 'sub'"
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
        // Exclude entire branch
        executer.withTasks(":d").withArguments("-x", "c").run().assertTasksExecuted(":d");
        // Exclude direct dependency
        executer.withTasks(":d").withArguments("-x", "b").run().assertTasksExecuted(":a", ":c", ":d");
        // Exclude using paths and multi-project
        executer.withTasks("d").withArguments("-x", "c").run().assertTasksExecuted(":d", ":sub:d");
        executer.withTasks("d").withArguments("-x", "sub:c").run().assertTasksExecuted(":a", ":b", ":c", ":d", ":sub:d");
        executer.withTasks("d").withArguments("-x", ":sub:c").run().assertTasksExecuted(":a", ":b", ":c", ":d", ":sub:d");
        executer.withTasks("d").withArguments("-x", "d").run().assertTasksExecuted();
        // Project defaults
        executer.withArguments("-x", "b").run().assertTasksExecuted(":a", ":c", ":d", ":sub:c", ":sub:d");
        // Unknown task
        executer.withTasks("d").withArguments("-x", "unknown").runWithFailure().assertThatDescription(startsWith("Task 'unknown' not found in root project"));
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
        executer.inDirectory(file('sub')).withTasks('c').withArguments('-x', 'a').run().assertTasksExecuted(':a', ':sub:b', ':sub:c')
    }

    def 'can use camel-case matching to exclude tasks'() {
        buildFile << """
task someDep
task someOtherDep
task someTask(dependsOn: [someDep, someOtherDep])
"""

        expect:
        executer.withTasks("someTask").withArguments("-x", "sODep").run().assertTasksExecuted(":someDep", ":someTask")
        executer.withTasks("someTask").withArguments("-x", ":sODep").run().assertTasksExecuted(":someDep", ":someTask")
    }

    def 'can combine exclude task filters'() {
        buildFile << """
task someDep
task someOtherDep
task someTask(dependsOn: [someDep, someOtherDep])
"""

        expect:
        executer.withTasks("someTask").withArguments("-x", "someDep", "-x", "someOtherDep").run().assertTasksExecuted(":someTask")
        executer.withTasks("someTask").withArguments("-x", ":someDep", "-x", ":someOtherDep").run().assertTasksExecuted(":someTask")
        executer.withTasks("someTask").withArguments("-x", "sODep", "-x", "soDep").run().assertTasksExecuted(":someTask")
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
        executer.withTasks("d").withArguments("-x", "a").run().assertTasksExecuted(":b", ":c", ":d");
        executer.withTasks("b", "a").withArguments("-x", ":a").run().assertTasksExecuted(":b", ":sub:a");
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2022")
    def tryingToInstantiateTaskDirectlyFailsWithGoodErrorMessage() {
        buildFile << """
    new DefaultTask()
"""
        when:
        fails "tasks"

        then:
        failure.assertHasCause("Task of type 'org.gradle.api.DefaultTask' has been instantiated directly which is not supported")
    }

    def "sensible error message for circular task dependency"() {
        buildFile << """
    task a(dependsOn: 'b')
    task b(dependsOn: 'a')
"""
        when:
        fails 'b'

        then:
        failure.assertHasDescription """Circular dependency between the following tasks:
:a
\\--- :b
     \\--- :a (*)

(*) - details omitted (listed previously)"""
    }

    @Ignore("Re-enable when work on realising only the required tasks instead of the whole task container is finished")
    def "placeholder actions not triggered when not requested"() {
        when:
        buildFile << """
        task thing
        tasks.addPlaceholderAction("b", DefaultTask) {
            throw new RuntimeException()
        }
        task otherThing { dependsOn tasks.thing }
"""
        then:
        succeeds 'thing'
        succeeds 'th'
        succeeds 'otherThing'
        succeeds 'oTh'
    }

    def "explicit tasks are preferred over placeholder tasks"() {
        buildFile << """
        task someTask << {println "explicit sometask"}
        tasks.addPlaceholderAction("someTask", DefaultTask) {
            println  "placeholder action triggered"
            it.doLast { throw new RuntimeException() }
        }
"""
        when:
        succeeds 'sometask'

        then:
        output.contains("explicit sometask")

        when:
        succeeds 'someT'

        then:
        output.contains("explicit sometask")
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
        when:
        succeeds 'c', 'd'

        then:
        result.assertTasksExecuted(':d', ':b', ':a', ':c')
    }

    def "finalizer task is executed if a finalized task is executed"() {
        buildFile << """
    task a
    task b {
        doLast {}
        finalizedBy a
    }
"""
        when:
        succeeds 'b'

        then:
        ":a" in executedTasks
    }

    def "finalizer task is executed even if the finalised task fails"() {
        buildFile << """
    task a
    task b  {
        doLast { throw new RuntimeException() }
        finalizedBy a
    }
"""
        when:
        fails 'b'

        then:
        ":a" in executedTasks
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
        when:
        fails 'c'

        then:
        !(":b" in executedTasks)
    }

    def "sensible error message for circular task dependency due to mustRunAfter"() {
        buildFile << """
    task a {
        mustRunAfter 'b'
    }
    task b(dependsOn: 'a')
"""
        when:
        fails 'b'

        then:
        failure.assertHasDescription """Circular dependency between the following tasks:
:a
\\--- :b
     \\--- :a (*)

(*) - details omitted (listed previously)"""
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

        when:
        fails "explode"

        then:
        failure.assertHasCause "java.lang.Exception: I am the checked exception"
    }

    def "honours shouldRunAfter task ordering"() {
        buildFile << """

    class NotParallel extends DefaultTask {}

    task a(type: NotParallel) {
        dependsOn 'b'
    }
    task b(type: NotParallel) {
        shouldRunAfter 'c'
    }
    task c(type: NotParallel)
    task d(type: NotParallel) {
        dependsOn 'c'
    }
"""
        when:
        succeeds 'a', 'd'

        then:
        executedTasks == [':c', ':b', ':a', ':d']
    }

    def "multiple should run after ordering can be ignored for one execution plan"() {
        buildFile << """
    class NotParallel extends DefaultTask {}

    task a(type: NotParallel) {
        dependsOn 'b', 'h'
    }
    task b(type: NotParallel) {
        dependsOn 'c'
    }
    task c(type: NotParallel) {
        dependsOn 'g'
        shouldRunAfter 'd'
    }
    task d(type: NotParallel) {
        finalizedBy 'e'
        dependsOn 'f'
    }
    task e(type: NotParallel)
    task f(type: NotParallel) {
        dependsOn 'c'
    }
    task g(type: NotParallel) {
        shouldRunAfter 'h'
    }
    task h(type: NotParallel) {
        dependsOn 'b'
    }
"""

        when:
        succeeds 'a', 'd'

        then:
        executedTasks == [':g', ':c', ':b', ':h', ':a', ':f', ':d', ':e']
    }
}