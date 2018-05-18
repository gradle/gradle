/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.scheduler

import org.gradle.api.CircularReferenceException
import org.gradle.api.specs.Spec
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.TextUtil.toPlatformLineSeparators
import static org.gradle.util.WrapUtil.toList

abstract class AbstractSchedulingTest extends Specification {

    protected abstract void addToGraph(List tasks)
    protected abstract void determineExecutionPlan()
    protected void addToGraphAndPopulate(List nodes) {
        addToGraph(nodes)
        determineExecutionPlan()
    }
    protected abstract void relationships(Map options, def task)
    protected abstract void executes(Object... expectedTasks)
    protected def task(String name) {
        task([:], name)
    }
    protected abstract void filtered(Object... expectedTasks)
    protected abstract def createTask(String name)
    protected abstract def task(Map options, final String name)
    protected abstract def filteredTask(final String name)
    protected abstract void useFilter(Spec filter)
    protected abstract Set getAllTasks()
    protected abstract List getExecutedTasks()
    protected abstract List<? extends Throwable> getFailures()
    protected abstract void continueOnFailure()

    def "schedules tasks in dependency order"() {
        given:
        def a = task("a")
        def b = task("b", dependsOn: [a])
        def c = task("c", dependsOn: [b, a])
        def d = task("d", dependsOn: [c])

        when:
        addToGraphAndPopulate([d])

        then:
        executes(a, b, c, d)
    }

    def "common tasks in separate batches are schedules only once"() {
        def a = task("a")
        def b = task("b")
        def c = task("c", dependsOn: [a, b])
        def d = task("d", shouldRunAfter: [c])
        def e = task("e", dependsOn: [b, d])

        when:
        addToGraph(toList(c))
        addToGraph(toList(e))
        determineExecutionPlan()

        then:
        executes(a, b, c, d, e)
    }

    def "all dependencies scheduled when adding tasks"() {
        def a = task("a")
        def b = task("b", dependsOn: [a])
        def c = task("c", dependsOn: [b, a])
        def d = task("d", dependsOn: [c])

        when:
        addToGraphAndPopulate(toList(d))

        then:
        executes(a, b, c, d)
    }

    @Unroll
    def "#orderingRule ordering is honoured for tasks added separately to graph"() {
        def a = task("a")
        def b = task("b", dependsOn: [a])
        def c = task("c", (orderingRule): [b])

        when:
        addToGraph([c])
        addToGraph([b])
        determineExecutionPlan()

        then:
        executes(a, b, c)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    @Unroll
    def "#orderingRule ordering is honoured for dependencies"() {
        def b = task("b")
        def a = task("a", (orderingRule): [b])
        def c = task("c", dependsOn: [a, b])

        when:
        addToGraphAndPopulate([c])

        then:
        executes(b, a, c)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "cyclic should run after ordering is ignored in complex task graph"() {
        given:

        def e = createTask("e")
        def x = task("x", dependsOn: [e])
        def a = task("a", shouldRunAfter: [x])
        def b = task("b", shouldRunAfter: [a])
        def c = task("c", shouldRunAfter: [b])
        def f = task("f", dependsOn: [x], shouldRunAfter: [c])
        def d = task("d", dependsOn: [f], shouldRunAfter: [c])
        relationships(e, shouldRunAfter: [d])
        def build = task("build", dependsOn: [x, a, b, c, d, e])

        when:
        addToGraphAndPopulate([build])

        then:
        executes(e, x, a, b, c, f, d, build)
    }

    @Unroll
    def "#orderingRule does not pull in tasks that are not in the graph"() {
        def a = task("a")
        def b = task("b", (orderingRule): [a])

        when:
        addToGraphAndPopulate([b])

        then:
        executes(b)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "finalizer tasks are executed if a finalized task is added to the graph"() {
        def finalizer = task("a")
        def finalized = task("b", finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized, finalizer)
    }

    def "finalizer tasks and their dependencies are executed even in case of a task failure"() {
        def finalizerDependency = task("finalizerDependency")
        def finalizer1 = task("finalizer1", dependsOn: [finalizerDependency])
        def finalized1 = task("finalized1", finalizedBy: [finalizer1])
        def finalizer2 = task("finalizer2")
        def finalized2 = task("finalized2", finalizedBy: [finalizer2], failure: new RuntimeException("failure"))

        when:
        addToGraphAndPopulate([finalized1, finalized2])

        then:
        executes(finalized1, finalizerDependency, finalizer1, finalized2, finalizer2)
    }

    def "finalizer task is not added to the graph if it is filtered"() {
        given:
        def finalizer = filteredTask("finalizer")
        def finalized = task("finalized", finalizedBy: [finalizer])
        when:
        useFilter { t -> t != finalizer }
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized)
    }

    def "finalizer tasks and their dependencies are not executed if finalized task did not run"() {
        def finalizerDependency = task("finalizerDependency")
        def finalizer = task("finalizer", dependsOn: [finalizerDependency])
        def finalizedDependency = task("finalizedDependency", failure: new RuntimeException("failure"))
        def finalized = task("finalized", dependsOn: [finalizedDependency], finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([finalized])

        then:
        allTasks == ([finalizedDependency, finalized, finalizerDependency, finalizer] as Set)
        executedTasks == [finalizedDependency]
    }

    def "finalizer tasks and their dependencies are executed if they are previously required even if the finalized task did not run"() {
        def finalizerDependency = task("finalizerDependency")
        def finalizer = task("finalizer", dependsOn: [finalizerDependency])
        def finalizedDependency = task("finalizedDependency", failure: new RuntimeException("failure"))
        def finalized = task("finalized", dependsOn: [finalizedDependency], finalizedBy: [finalizer])
        continueOnFailure()

        when:
        addToGraphAndPopulate([finalizer, finalized])

        then:
        allTasks == ([finalizedDependency, finalized, finalizerDependency, finalizer] as Set)
        executedTasks == [finalizedDependency, finalizerDependency, finalizer]
    }

    def "finalizer tasks and their dependencies are executed if they are later required via dependency even if the finalized task did not do any work"() {
        def finalizerDependency = task("finalizerDependency")
        def finalizer = task("finalizer", dependsOn: [finalizerDependency])
        def dependsOnFinalizer = task("dependsOnFinalizer", dependsOn: [finalizer])
        def finalized = task("finalized", finalizedBy: [finalizer], didWork: false)

        when:
        addToGraph([finalized])
        addToGraph([dependsOnFinalizer])
        determineExecutionPlan()

        then:
        executes(finalized, finalizerDependency, finalizer, dependsOnFinalizer)
    }

    def "finalizer tasks run as soon as possible for tasks that depend on finalized tasks"() {
        def finalizer = task("finalizer")
        def finalized = task("finalized", finalizedBy: [finalizer])
        def dependsOnFinalized = task("dependsOnFinalized", dependsOn: [finalized])

        when:
        addToGraphAndPopulate([dependsOnFinalized])

        then:
        executes(finalized, finalizer, dependsOnFinalized)
    }

    def "multiple finalizer tasks may have relationships between each other"() {
        def f2 = task("f2")
        def f1 = task("f1", dependsOn: [f2])
        def finalized = task("finalized", finalizedBy: [f1, f2])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized, f2, f1)
    }

    def "multiple finalizer tasks may have relationships between each other via some other task"() {
        def f2 = task("f2")
        def d = task("d", dependsOn:[f2] )
        def f1 = task("f1", dependsOn: [d])
        def finalized = task("finalized", finalizedBy: [f1, f2])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized, f2, d, f1)
    }

    @Issue("GRADLE-2957")
    def "task with a dependency and a finalizer both having a common finalizer"() {
        // Finalizer task
        def finalTask = task('finalTask')

        // Task with this finalizer
        def dependency = task('dependency', finalizedBy: [finalTask])
        def finalizer = task('finalizer', finalizedBy: [finalTask])

        // Task to call, with the same finalizer than one of its dependencies
        def requestedTask = task('requestedTask', dependsOn: [dependency], finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([requestedTask])

        then:
        executes(dependency, requestedTask, finalizer, finalTask)
    }

    @Issue("GRADLE-2983")
    def "multiple finalizer tasks with relationships via other tasks scheduled from multiple tasks"() {
        //finalizers with a relationship via a dependency
        def f1 = task("f1")
        def dep = task("dep", dependsOn:[f1] )
        def f2 = task("f2", dependsOn: [dep])

        //2 finalized tasks
        def finalized1 = task("finalized1", finalizedBy: [f1, f2])
        def finalized2 = task("finalized2", finalizedBy: [f1, f2])

        //tasks that depends on finalized, we will execute them
        def df1 = task("df1", dependsOn: [finalized1, finalized2])
        def df2 = task("df2", dependsOn: [finalized2])

        when:
        addToGraphAndPopulate([df1, df2])

        then:
        executes(finalized1, finalized2, f1, dep, f2, df1, df2)
    }

    @Unroll
    def "finalizer tasks run as soon as possible for tasks that #orderingRule finalized tasks"() {
        def finalizer = task("finalizer")
        def finalized = task("finalized", finalizedBy: [finalizer])
        def runsAfterFinalized = task("runsAfterFinalized", (orderingRule): [finalized])

        when:
        addToGraphAndPopulate([runsAfterFinalized, finalized])

        then:
        executes(finalized, finalizer, runsAfterFinalized)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    @Unroll
    def "finalizer tasks run as soon as possible but after its #orderingRule tasks"() {
        def finalizer = createTask("finalizer")
        def finalized = task("finalized", finalizedBy: [finalizer])
        def dependsOnFinalized = task("dependsOnFinalized", dependsOn: [finalized])
        relationships(finalizer, (orderingRule): [dependsOnFinalized])

        when:
        addToGraphAndPopulate([dependsOnFinalized])

        then:
        executes(finalized, dependsOnFinalized, finalizer)

        where:
        orderingRule << ['dependsOn', 'mustRunAfter' , 'shouldRunAfter']
    }

    def "cannot add task with circular reference"() {
        def a = createTask("a")
        def b = task("b", dependsOn: [a])
        def c = task("c", dependsOn: [b])
        def d = task("d")
        relationships(a, dependsOn: [c, d])

        when:
        addToGraphAndPopulate([c])

        then:
        def e = thrown CircularReferenceException
        e.message == toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "cannot add a task with must run after induced circular reference"() {
        def a = createTask("a")
        def b = task("b", mustRunAfter: [a])
        def c = task("c", dependsOn: [b])
        relationships(a, dependsOn: [c])

        when:
        addToGraphAndPopulate([a])

        then:
        def e = thrown CircularReferenceException
        e.message == toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "cannot add a task with must run after induced circular reference that was previously in graph but not required"() {
        def a = createTask("a")
        def b = task("b", mustRunAfter: [a])
        def c = task("c", dependsOn: [b])
        def d = task("d", dependsOn: [c])
        relationships(a, mustRunAfter: [c])
        addToGraph([d])

        when:
        addToGraph([a])
        determineExecutionPlan()

        then:
        def e = thrown CircularReferenceException
        e.message == toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "should run after ordering is ignored if it is in a middle of a circular reference"() {
        def a = task("a")
        def b = task("b")
        def c = task("c")
        def d = createTask("d")
        def e = task("e", dependsOn: [a, b, c, d])
        def f = task("f", dependsOn: [e])
        def g = task("g", dependsOn: [c, f])
        def h = task("h", dependsOn: [b, g])
        relationships(d, shouldRunAfter: [g])

        when:
        addToGraphAndPopulate([e, h])

        then:
        executedTasks == [a, b, c, d, e, f, g, h]
    }

    @Issue("GRADLE-3166")
    def "multiple should run after declarations are removed if causing circular reference"() {
        def a = createTask("a")
        def b = createTask("b")
        def c = createTask("c")

        relationships(a, dependsOn: [c])
        relationships(b, dependsOn: [a, c])
        relationships(c, shouldRunAfter: [b, a])

        when:
        addToGraphAndPopulate([b])

        then:
        executedTasks == [c, a, b]
    }

    def "should run after ordering is ignored if it is at the end of a circular reference"() {
        def a = createTask("a")
        def b = task("b", dependsOn: [a])
        def c = task("c", dependsOn: [b])
        relationships(a, shouldRunAfter: [c])

        when:
        addToGraphAndPopulate([c])

        then:
        executedTasks == [a, b, c]
    }

    @Issue("GRADLE-3127")
    def "circular dependency detected with shouldRunAfter dependencies in the graph"() {
        def a = createTask("a")
        def b = task("b")
        def c = createTask("c")
        def d = task("d", dependsOn: [a, b, c])
        relationships(a, shouldRunAfter: [b])
        relationships(c, dependsOn: [d])

        when:
        addToGraphAndPopulate([d])

        then:
        CircularReferenceException e = thrown()
        e.message == toPlatformLineSeparators("""Circular dependency between the following tasks:
:c
\\--- :d
     \\--- :c (*)

(*) - details omitted (listed previously)
""")
    }

    def "stops returning tasks on task execution failure"() {
        RuntimeException exception = new RuntimeException("failure")

        when:
        def a = task([failure: exception],"a")
        def b = task("b")
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]
        failures == [exception]
    }

    def "stops returning tasks on first task failure when no failure handler provided"() {
        RuntimeException failure = new RuntimeException("failure")
        def a = task("a", failure: failure)
        def b = task("b")

        when:
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]
        failures == [failure]
    }

    def "stops execution on task failure when failure handler indicates that execution should stop"() {
        RuntimeException failure = new RuntimeException("failure")
        def a = task("a", failure: failure)
        def b = task("b")

        when:
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]
        failures == [failure]
    }

    def "continues to return tasks and rethrows failure on completion when failure handler indicates that execution should continue"() {
        RuntimeException failure = new RuntimeException()
        def a = task("a", failure: failure)
        def b = task("b")
        continueOnFailure()

        when:
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a, b]
        failures == [failure]
    }

    @Unroll
    def "continues to return tasks when failure handler does not abort execution and tasks are #orderingRule dependent"() {
        RuntimeException failure = new RuntimeException()
        def a = task("a", failure: failure)
        def b = task("b", (orderingRule): [a])
        continueOnFailure()

        when:
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a, b]
        failures == [failure]

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "does not attempt to execute tasks whose dependencies failed to execute"() {
        RuntimeException failure = new RuntimeException()
        final def a = task("a", failure: failure)
        final def b = task("b", dependsOn: [a])
        final def c = task("c", shouldRunAfter: [a])
        continueOnFailure()

        when:
        addToGraphAndPopulate([b, c])

        then:
        executedTasks == [a, c]
        failures == [failure]
    }

    def "does not build graph for or execute filtered tasks"() {
        given:
        def a = filteredTask("a")
        def b = task("b")

        when:
        useFilter { t -> t != a }
        addToGraphAndPopulate([a, b])

        then:
        executes(b)
        filtered(a)
    }

    def "does not build graph for or execute filtered dependencies"() {
        given:
        def a = filteredTask("a")
        def b = task("b")
        def c = task("c", dependsOn: [a, b])

        when:
        useFilter { t -> t != a }
        addToGraphAndPopulate([c])

        then:
        executes(b, c)
        filtered(a)
    }

    @Unroll
    def "does not build graph for or execute filtered tasks reachable via #orderingRule task ordering"() {
        given:
        def a = filteredTask("a")
        def b = task("b", (orderingRule): [a])
        def c = task("c", dependsOn: [a])

        when:
        useFilter { t -> t != a }
        addToGraphAndPopulate([b, c])

        then:
        executes(b, c)
        filtered(a)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "will execute a task whose dependencies have been filtered"() {
        given:
        def b = filteredTask("b")
        def c = task("c", dependsOn: [b])

        when:
        useFilter { t -> t != b }
        addToGraphAndPopulate([c])

        then:
        executes(c)
        filtered(b)
    }

}
