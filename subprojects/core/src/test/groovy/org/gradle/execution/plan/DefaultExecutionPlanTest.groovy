/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.plan


import org.gradle.api.BuildCancelledException
import org.gradle.api.CircularReferenceException
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.api.problems.Problems
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.internal.file.Stat
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.Path
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import java.util.function.Consumer

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE
import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators
import static org.gradle.util.internal.WrapUtil.toList

class DefaultExecutionPlanTest extends AbstractExecutionPlanSpec {
    DefaultExecutionPlan executionPlan
    DefaultFinalizedExecutionPlan finalizedPlan

    def accessHierarchies = new ExecutionNodeAccessHierarchies(CASE_SENSITIVE, Stub(Stat))
    def taskNodeFactory = new TaskNodeFactory(thisBuild, Stub(BuildTreeWorkGraphController), nodeValidator, new TestBuildOperationExecutor(), accessHierarchies, Stub(Problems))
    def dependencyResolver = new TaskDependencyResolver([new TaskNodeDependencyResolver(taskNodeFactory)])

    def setup() {
        executionPlan = newExecutionPlan()
    }

    private DefaultExecutionPlan newExecutionPlan() {
        executionPlan?.close()
        new DefaultExecutionPlan(Path.ROOT.toString(), taskNodeFactory, new OrdinalGroupFactory(), dependencyResolver, accessHierarchies.outputHierarchy, accessHierarchies.destroyableHierarchy, coordinator)
    }

    def "schedules tasks in dependency order"() {
        given:
        Task a = task("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", dependsOn: [b, a])
        Task d = task("d", dependsOn: [c])

        when:
        addToGraphAndPopulate([d])

        then:
        executes(a, b, c, d)
    }

    def "schedules task dependencies in name order when there are no dependencies between them"() {
        given:
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d", dependsOn: [b, a, c])

        when:
        addToGraphAndPopulate([d])

        then:
        executes(a, b, c, d)
    }

    def "schedules a single batch of tasks in name order"() {
        given:
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")

        when:
        addToGraphAndPopulate(toList(b, c, a))

        then:
        executes(a, b, c)
    }

    def "schedules separately added tasks in order added"() {
        given:
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d")

        when:
        addToGraph(toList(c, b))
        addToGraph(toList(d, a))
        populateGraph()

        then:
        executes(b, c, a, d)
    }

    def "schedules #orderingRule task dependencies in name order"() {
        given:
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", (orderingRule): [b, a])
        Task d = task("d", dependsOn: [b, a])

        when:
        addToGraphAndPopulate([c, d])

        then:
        executes(a, b, c, d)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "common tasks in separate batches are schedules only once"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", dependsOn: [a, b])
        Task d = task("d")
        Task e = task("e", dependsOn: [b, d])

        when:
        addToGraph(toList(c))
        addToGraph(toList(e))
        populateGraph()

        then:
        executes(a, b, c, d, e)
    }

    def "all dependencies scheduled when adding tasks"() {
        Task a = task("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", dependsOn: [b, a])
        Task d = task("d", dependsOn: [c])

        when:
        addToGraphAndPopulate(toList(d))

        then:
        executes(a, b, c, d)
    }

    def "#orderingRule ordering is honoured for tasks added separately to graph"() {
        Task a = task("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", (orderingRule): [b])

        when:
        addToGraph([c])
        addToGraph([b])
        populateGraph()

        then:
        executes(a, b, c)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "#orderingRule ordering is honoured for dependencies"() {
        Task b = task("b")
        Task a = task("a", (orderingRule): [b])
        Task c = task("c", dependsOn: [a, b])

        when:
        addToGraphAndPopulate([c])

        then:
        executes(b, a, c)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "mustRunAfter dependencies are scheduled before regular dependencies"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", dependsOn: [a], mustRunAfter: [b])
        Task d = task("d", dependsOn: [b])

        when:
        addToGraphAndPopulate([c, d])

        then:
        executes(b, a, c, d)
    }

    def "shouldRunAfter dependencies are scheduled before mustRunAfter dependencies"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", mustRunAfter: [a], shouldRunAfter: [b])
        Task d = task("d", dependsOn: [a, b])

        when:
        addToGraphAndPopulate([c, d])

        then:
        executes(b, a, c, d)
    }

    def "cyclic should run after ordering is ignored in complex task graph"() {
        given:

        Task e = task("e")
        Task x = task("x", dependsOn: [e])
        Task f = task("f", dependsOn: [x])
        Task a = task("a", shouldRunAfter: [x])
        Task b = task("b", shouldRunAfter: [a])
        Task c = task("c", shouldRunAfter: [b])
        Task d = task("d", dependsOn: [f], shouldRunAfter: [c])
        relationships(e, shouldRunAfter: [d])
        Task build = task("build", dependsOn: [x, a, b, c, d, e])

        when:
        addToGraphAndPopulate([build])

        then:
        executes(e, x, a, b, c, f, d, build)
    }

    def "#orderingRule does not pull in tasks that are not in the graph"() {
        Task a = task("a")
        Task b = task("b", (orderingRule): [a])

        when:
        addToGraphAndPopulate([b])

        then:
        executes(b)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "finalizer tasks are executed if a finalized task is added to the graph"() {
        Task finalizer = task("a")
        Task finalized = task("b", finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized, finalizer)
    }

    def "finalizer tasks and their dependencies are executed even in case of a task failure"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer1 = task("finalizer1", dependsOn: [finalizerDependency])
        Task finalized1 = task("finalized1", finalizedBy: [finalizer1])
        Task finalizer2 = task("finalizer2")
        Task finalized2 = task("finalized2", finalizedBy: [finalizer2], failure: new RuntimeException("failure"))

        when:
        addToGraphAndPopulate([finalized1, finalized2])

        then:
        executes(finalized1, finalizerDependency, finalizer1, finalized2, finalizer2)
    }

    def "finalizer task is not added to the graph if it is filtered"() {
        given:
        Task finalizer = filteredTask("finalizer")
        Task finalized = task("finalized", finalizedBy: [finalizer])
        Spec<Task> filter = Mock() {
            isSatisfiedBy(_) >> { Task t -> t != finalizer }
        }

        when:
        executionPlan.addFilter(filter)
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized)
    }

    def "finalizer tasks and their dependencies are not executed if finalized task did not run due to failed dependency"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task finalizedDependency = task("finalizedDependency", failure: new RuntimeException("failure"))
        Task finalized = task("finalized", dependsOn: [finalizedDependency], finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executionPlan.tasks as List == [finalizedDependency, finalized, finalizerDependency, finalizer]
        executedTasks == [finalizedDependency]
    }

    def "finalizer tasks and their dependencies are not executed if finalized task did not run due to failure in unrelated task"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task broken = task("broken", failure: new RuntimeException("failure"))
        Task finalized = task("finalized", finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([broken, finalized])

        then:
        executionPlan.tasks as List == [broken, finalized, finalizerDependency, finalizer]
        executedTasks == [broken]
    }

    def "finalizer tasks and their dependencies are executed if they are previously required even if the finalized task did not run"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task finalizedDependency = task("finalizedDependency", failure: new RuntimeException("failure"))
        Task finalized = task("finalized", dependsOn: [finalizedDependency], finalizedBy: [finalizer])
        executionPlan.setContinueOnFailure(true)

        when:
        addToGraphAndPopulate([finalizer, finalized])

        then:
        executionPlan.tasks as List == [finalizedDependency, finalized, finalizerDependency, finalizer]
        executedTasks == [finalizedDependency, finalizerDependency, finalizer]
    }

    def "finalizer tasks and their dependencies are executed if they are later required via dependency even if the finalized task did not do any work"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task dependsOnFinalizer = task("dependsOnFinalizer", dependsOn: [finalizer])
        Task finalized = task("finalized", finalizedBy: [finalizer], didWork: false)

        when:
        addToGraph([finalized])
        addToGraphAndPopulate([dependsOnFinalizer])

        then:
        executes(finalized, finalizerDependency, finalizer, dependsOnFinalizer)
    }

    def "finalizer tasks run as soon as possible for tasks that depend on finalized tasks"() {
        Task finalizer = task("finalizer")
        Task finalized = task("finalized", finalizedBy: [finalizer])
        Task dependsOnFinalized = task("dependsOnFinalized", dependsOn: [finalized])

        when:
        addToGraphAndPopulate([dependsOnFinalized])

        then:
        executes(finalized, finalizer, dependsOnFinalized)
    }

    def "multiple finalizer tasks may have relationships between each other"() {
        Task f2 = task("f2")
        Task f1 = task("f1", dependsOn: [f2])
        Task finalized = task("finalized", finalizedBy: [f1, f2])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized, f2, f1)
    }

    def "multiple finalizer tasks may have relationships between each other via some other task"() {
        Task f2 = task("f2")
        Task d = task("d", dependsOn: [f2])
        Task f1 = task("f1", dependsOn: [d])
        Task finalized = task("finalized", finalizedBy: [f1, f2])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized, f2, d, f1)
    }

    @Issue("GRADLE-2957")
    def "task with a dependency and a finalizer both having a common finalizer"() {
        // Finalizer task
        Task finalTask = task('finalTask')

        // Task with this finalizer
        Task dependency = task('dependency', finalizedBy: [finalTask])
        Task finalizer = task('finalizer', finalizedBy: [finalTask])

        // Task to call, with the same finalizer than one of its dependencies
        Task requestedTask = task('requestedTask', dependsOn: [dependency], finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([requestedTask])

        then:
        executes(dependency, requestedTask, finalizer, finalTask)
    }

    @Issue("GRADLE-2983")
    def "multiple finalizer tasks with relationships via other tasks scheduled from multiple tasks"() {
        //finalizers with a relationship via a dependency
        Task f1 = task("f1")
        Task dep = task("dep", dependsOn: [f1])
        Task f2 = task("f2", dependsOn: [dep])

        //2 finalized tasks
        Task finalized1 = task("finalized1", finalizedBy: [f1, f2])
        Task finalized2 = task("finalized2", finalizedBy: [f1, f2])

        //tasks that depends on finalized, we will execute them
        Task df1 = task("df1", dependsOn: [finalized1])
        Task df2 = task("df2", dependsOn: [finalized2])

        when:
        addToGraphAndPopulate([df1, df2])

        then:
        executes(finalized1, finalized2, f1, dep, f2, df1, df2)
    }

    def "finalizer tasks run as soon as possible for tasks that #orderingRule finalized tasks"() {
        Task finalizer = task("finalizer")
        Task finalized = task("finalized", finalizedBy: [finalizer])
        Task runsAfterFinalized = task("runsAfterFinalized", (orderingRule): [finalized])

        when:
        addToGraphAndPopulate([runsAfterFinalized, finalized])

        then:
        executes(finalized, finalizer, runsAfterFinalized)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "finalizer tasks run as soon as possible but after its #orderingRule tasks"() {
        Task finalizer = createTask("finalizer")
        Task finalized = task("finalized", finalizedBy: [finalizer])
        Task dependsOnFinalized = task("dependsOnFinalized", dependsOn: [finalized])
        relationships(finalizer, (orderingRule): [dependsOnFinalized])

        when:
        addToGraphAndPopulate([dependsOnFinalized])

        then:
        executes(finalized, dependsOnFinalized, finalizer)

        where:
        orderingRule << ['dependsOn', 'mustRunAfter', 'shouldRunAfter']
    }

    def "finalizer groups that finalize each other do not form a cycle"() {
        given:
        TaskInternal finalizerA = createTask("finalizerA")
        TaskInternal finalizerB = createTask("finalizerB")
        TaskInternal finalizerDepA = task("finalizerDepA", finalizedBy: [finalizerB])
        TaskInternal finalizerDepB = task("finalizerDepB", finalizedBy: [finalizerA])
        relationships(finalizerA, dependsOn: [finalizerDepA])
        relationships(finalizerB, dependsOn: [finalizerDepB])
        TaskInternal entryPoint = task("entryPoint", finalizedBy: [finalizerA, finalizerB])

        when:
        addToGraphAndPopulate([entryPoint])

        then:
        executes(entryPoint, finalizerDepA, finalizerDepB, finalizerB, finalizerA)
    }

    def "cannot add task with circular reference"() {
        Task a = createTask("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", dependsOn: [b])
        Task d = task("d")
        relationships(a, dependsOn: [c, d])

        when:
        addToGraphAndPopulate([c])

        then:
        def e = thrown CircularReferenceException
        e.message == TextUtil.toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "cannot add a task with must run after induced circular reference"() {
        Task a = createTask("a")
        Task b = task("b", mustRunAfter: [a])
        Task c = task("c", dependsOn: [b])
        relationships(a, dependsOn: [c])

        when:
        addToGraphAndPopulate([a])

        then:
        def e = thrown CircularReferenceException
        e.message == TextUtil.toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "cannot add a task with must run after induced circular reference that was previously in graph but not required"() {
        Task a = createTask("a")
        Task b = task("b", mustRunAfter: [a])
        Task c = task("c", dependsOn: [b])
        Task d = task("d", dependsOn: [c])
        relationships(a, mustRunAfter: [c])
        addToGraph([d])

        when:
        addToGraph([a])
        populateGraph()

        then:
        def e = thrown CircularReferenceException
        e.message == TextUtil.toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "should run after ordering is ignored if it is in a middle of a circular reference"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d")
        Task e = task("e", dependsOn: [a, d])
        Task f = task("f", dependsOn: [e])
        Task g = task("g", dependsOn: [c, f])
        Task h = task("h", dependsOn: [b, g])
        relationships(d, shouldRunAfter: [g])

        when:
        addToGraphAndPopulate([e, h])

        then:
        executedTasks == [a, d, e, b, c, f, g, h]
    }

    @Issue("GRADLE-3166")
    def "multiple should run after declarations are removed if causing circular reference"() {
        Task a = createTask("a")
        Task b = createTask("b")
        Task c = createTask("c")

        relationships(a, dependsOn: [c])
        relationships(b, dependsOn: [a, c])
        relationships(c, shouldRunAfter: [b, a])

        when:
        addToGraphAndPopulate([b])

        then:
        executedTasks == [c, a, b]
    }

    def "should run after ordering is ignored if it is at the end of a circular reference"() {
        Task a = task("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", dependsOn: [b])
        relationships(a, shouldRunAfter: [c])

        when:
        addToGraphAndPopulate([c])

        then:
        executedTasks == [a, b, c]
    }

    @Issue("GRADLE-3127")
    def "circular dependency detected with shouldRunAfter dependencies in the graph"() {
        Task a = createTask("a")
        Task b = task("b")
        Task c = createTask("c")
        Task d = task("d", dependsOn: [a, b, c])
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

    @Issue("https://github.com/gradle/gradle/issues/2293")
    def "circular dependency detected with finalizedBy cycle in the graph where task finalizes itself"() {
        Task a = createTask("a")
        Task b = createTask("b")
        relationships(a, finalizedBy: [b])
        relationships(b, finalizedBy: [b])

        when:
        addToGraphAndPopulate([a])

        then:
        CircularReferenceException e = thrown()
        e.message == toPlatformLineSeparators("""Circular dependency between the following tasks:
:b
\\--- :b (*)

(*) - details omitted (listed previously)
""")
    }

    def "circular dependency detected with finalizedBy cycle in the graph"() {
        Task a = createTask("a")
        Task b = createTask("b")
        Task c = createTask("c")
        relationships(a, finalizedBy: [b])
        relationships(b, finalizedBy: [c])
        relationships(c, finalizedBy: [a])

        when:
        addToGraphAndPopulate([a])

        then:
        CircularReferenceException e = thrown()
        e.message == toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "stops returning tasks on first task execution failure"() {
        def failures = []
        RuntimeException exception = new RuntimeException("failure")

        when:
        Task a = task([failure: exception], "a")
        Task b = task("b")
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]

        when:
        finalizedPlan.collectFailures(failures)

        then:
        failures == [exception]
    }

    def "stops returning tasks when build is cancelled"() {
        def failures = []
        Task a = task("a")
        Task b = task("b")

        when:
        addToGraphAndPopulate([a, b])
        coordinator.withStateLock {
            finalizedPlan.cancelExecution()
        }

        then:
        executedTasks == []

        when:
        finalizedPlan.collectFailures(failures)

        then:
        failures.size() == 1
        failures[0] instanceof BuildCancelledException
    }

    def "continues to return tasks and rethrows failure on completion when failure handler indicates that execution should continue"() {
        def failures = []
        RuntimeException failure = new RuntimeException()
        Task a = task("a", failure: failure)
        Task b = task("b")

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a, b]

        when:
        finalizedPlan.collectFailures(failures)

        then:
        failures == [failure]
    }

    def "continues to return tasks when failure handler does not abort execution and tasks are #orderingRule dependent"() {
        def failures = []
        RuntimeException failure = new RuntimeException()
        Task a = task("a", failure: failure)
        Task b = task("b", (orderingRule): [a])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a, b]

        when:
        finalizedPlan.collectFailures(failures)

        then:
        failures == [failure]

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "does not attempt to execute tasks whose dependencies failed to execute"() {
        def failures = []
        RuntimeException failure = new RuntimeException()
        final Task a = task("a", failure: failure)
        final Task b = task("b", dependsOn: [a])
        final Task c = task("c")
        final Task d = task("d", dependsOn: [b, c])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate([d])

        then:
        executedTasks == [a, c]

        when:
        finalizedPlan.collectFailures(failures)

        then:
        failures == [failure]
    }

    def "does not attempt to execute tasks whose dependencies failed to execute in a previous plan"() {
        def failures = []
        RuntimeException failure = new RuntimeException()
        final Task a = task("a", failure: failure)
        final Task b = task("b", dependsOn: [a])
        final Task c = task("c")
        final Task d = task("d", dependsOn: [b, c])
        addToGraphAndPopulate([b])
        assert executedTasks == [a]

        when:
        executionPlan = newExecutionPlan()
        addToGraphAndPopulate([d])
        executionPlan.setContinueOnFailure(true)

        then:
        executionPlan.tasks as List == [b, c, d]
        executedTasks == [c]

        when:
        finalizedPlan.collectFailures(failures)

        then:
        failures == []
    }

    def "does not build graph for or execute filtered tasks"() {
        given:
        Task a = filteredTask("a")
        Task b = task("b")
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != a }

        when:
        executionPlan.addFilter(filter)
        addToGraphAndPopulate([a, b])

        then:
        executes(b)
        filtered(a)
    }

    def "does not build graph for or execute filtered dependencies"() {
        given:
        Task a = filteredTask("a")
        Task b = task("b")
        Task c = task("c", dependsOn: [a, b])
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != a }

        when:
        executionPlan.addFilter(filter)
        addToGraphAndPopulate([c])

        then:
        executes(b, c)
        filtered(a)
    }

    def "does not build graph for or execute filtered tasks reachable via #orderingRule task ordering"() {
        given:
        Task a = filteredTask("a")
        Task b = task("b", (orderingRule): [a])
        Task c = task("c", dependsOn: [a])
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != a }

        when:
        executionPlan.addFilter(filter)
        addToGraphAndPopulate([b, c])

        then:
        executes(b, c)
        filtered(a)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "will execute a task whose dependencies have been filtered"() {
        given:
        Task b = filteredTask("b")
        Task c = task("c", dependsOn: [b])
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != b }

        when:
        executionPlan.addFilter(filter)
        addToGraphAndPopulate([c])

        then:
        executes(c)
        filtered(b)
    }

    def "does not build graph for or execute tasks that have already executed in a previous plan"() {
        given:
        Task a = task("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", dependsOn: [b])
        addToGraphAndPopulate([b])
        executes(a, b)

        when:
        executionPlan = newExecutionPlan()
        addToGraphAndPopulate([c])

        then:
        executes(c)
    }

    def "does not build graph for or execute tasks that failed in a previous plan"() {
        given:
        Task a = task("a", failure: new RuntimeException("failure"))
        Task b = task("b", dependsOn: [a])
        Task c = task("c", dependsOn: [b])
        addToGraphAndPopulate([b])
        assert executedTasks == [a]

        when:
        executionPlan = newExecutionPlan()
        addToGraphAndPopulate([c])

        then:
        executionPlan.tasks as List == [b, c]
        executedTasks == []
    }

    def "builds graph for task that was filtered in a previous plan"() {
        given:
        Task a = task("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c")
        def filter = { it != b } as Spec<Task>
        executionPlan.addFilter(filter)
        addToGraphAndPopulate([b, c])
        executes(c)

        when:
        executionPlan = newExecutionPlan()
        addToGraphAndPopulate([b])

        then:
        executes(a, b)
    }

    def "builds graph for task whose execution was cancelled in a previous plan"() {
        given:
        Task a = task("a")
        Task b = task("b")
        addToGraphAndPopulate([a, b])
        coordinator.withStateLock {
            finalizedPlan.cancelExecution()
        }

        when:
        executionPlan = newExecutionPlan()
        addToGraphAndPopulate([a, b])

        then:
        executes(a, b)
    }

    def "builds graph for finalizer task whose execution was cancelled in a previous plan"() {
        given:
        Task a = task("a", failure: new RuntimeException())
        Task b = task("b")
        Task c = task("c", dependsOn: [a], finalizedBy: [b])
        addToGraphAndPopulate([c])
        executionPlan.setContinueOnFailure(continueOnFailure)
        assert executedTasks == [a]

        when:
        executionPlan = newExecutionPlan()
        addToGraphAndPopulate([b])

        then:
        executes(b)

        where:
        continueOnFailure << [true, false]
    }

    @Issue("https://github.com/gradle/gradle/issues/22853")
    def "builds graph for finalizer whose dependency was executed in a previous plan"() {
        given:
        Task dep = task("dep")
        Task finalizer1 = task("f1", dependsOn: [dep])
        Task finalizer2 = task("f2", dependsOn: [dep])
        Task finalizer3 = task("f3", dependsOn: [dep])
        Task a = task("a", finalizedBy: [finalizer1])
        Task b = task("b", finalizedBy: [finalizer2])
        Task c = task("c", finalizedBy: [finalizer3])
        addToGraphAndPopulate([a])
        executes(a, dep, finalizer1)

        when:
        executionPlan = newExecutionPlan()
        addToGraphAndPopulate([b])

        then:
        executes(b, finalizer2)

        when:
        // Need to run 3 times to trigger the issue
        executionPlan = newExecutionPlan()
        addToGraphAndPopulate([c])

        then:
        executes(c, finalizer3)
    }

    def "required nodes added to the graph are executed in dependency order"() {
        given:
        def node1 = requiredNode()
        def node2 = requiredNode(node1)
        def node3 = requiredNode(node2)
        executionPlan.setScheduledWork(scheduledWork(node3, node1, node2))

        when:
        populateGraph()

        then:
        executesNodes(node1, node2, node3)
    }

    def "notifies listener when task is executed"() {
        def listener = Mock(Consumer)
        executionPlan.onComplete(listener)

        def task1 = task("a")
        def task2 = task("b")

        when:
        addToGraphAndPopulate([task1, task2])
        executes(task1, task2)

        then:
        1 * listener.accept({ it.task == task1 })
        1 * listener.accept({ it.task == task2 })
        0 * listener._
    }

    def "notifies listener when task fails"() {
        def listener = Mock(Consumer)
        executionPlan.onComplete(listener)

        def task1 = task("a")
        def task2 = task("b", failure: new RuntimeException("broken"))

        when:
        addToGraphAndPopulate([task1, task2])
        executes(task1, task2)

        then:
        1 * listener.accept({ it.task == task1 })
        1 * listener.accept({ it.task == task2 })
        0 * listener._
    }

    def "notifies listener when task is skipped due to failed dependency"() {
        def listener = Mock(Consumer)
        executionPlan.onComplete(listener)

        def task1 = task("a", failure: new RuntimeException("broken"))
        def task2 = task("b", dependsOn: [task1])
        def task3 = task("c", dependsOn: [task2])

        when:
        addToGraphAndPopulate([task1, task2, task3])
        assert executedTasks == [task1]

        then:
        1 * listener.accept({ it.task == task1 })
        1 * listener.accept({ it.task == task2 })
        1 * listener.accept({ it.task == task3 })
        0 * listener._
    }

    def "collects failure but does not abort execution when task completion listener fails"() {
        def listener = Mock(Consumer)
        def failure = new RuntimeException()
        executionPlan.onComplete(listener)

        def task1 = task("a")
        def task2 = task("b", dependsOn: [task1])
        def task3 = task("c", dependsOn: [task2])

        when:
        addToGraphAndPopulate([task3])
        executes(task1, task2, task3)
        def failures = []
        finalizedPlan.collectFailures(failures)

        then:
        failures == [failure]
        1 * listener.accept({ it.task == task1 }) >> { throw failure }
        1 * listener.accept({ it.task == task2 })
        1 * listener.accept({ it.task == task3 })
        0 * listener._
    }

    private Node requiredNode(Node... dependencies) {
        node(dependencies).tap {
            require()
            dependenciesProcessed()
        }
    }

    private Node node(Node... dependencies) {
        def action = Stub(WorkNodeAction)
        _ * action.owningProject >> null
        _ * action.preExecutionNode >> null
        def node = new ActionNode(action)
        dependencies.each {
            node.addDependencySuccessor(it)
        }
        node.dependenciesProcessed()
        return node
    }

    private void addToGraph(List tasks) {
        executionPlan.addEntryTasks(tasks)
    }

    private void addToGraphAndPopulate(List tasks) {
        addToGraph(tasks)
        populateGraph()
    }

    private void populateGraph() {
        executionPlan.determineExecutionPlan()
        finalizedPlan = executionPlan.finalizePlan()
    }

    void executes(Task... expectedTasks) {
        assert executionPlan.tasks as List == expectedTasks as List
        assert executedTasks == expectedTasks as List
    }

    void executesNodes(Node... expectedNodes) {
        assert executedNodes == expectedNodes as List
    }

    void filtered(Task... expectedTasks) {
        assert executionPlan.filteredTasks == expectedTasks as Set
    }

    List<Task> getExecutedTasks() {
        def tasks = []
        def nodes = executedNodes
        int i = 0
        while (i < nodes.size) {
            def node = nodes[i]
            assert node instanceof ResolveMutationsNode
            assert nodes.size() > i + 1
            def taskNode = nodes[i + 1]
            assert taskNode instanceof LocalTaskNode
            assert taskNode == node.node
            tasks << taskNode.task
            i += 2
        }
        return tasks
    }

    List<Node> getExecutedNodes() {
        def nodes = []
        coordinator.withStateLock {
            while (finalizedPlan.executionState() != WorkSource.State.NoMoreWorkToStart) {
                assert finalizedPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart // There should always be a node ready to start when executing sequentially
                def selection = finalizedPlan.selectNext()
                if (selection.noMoreWorkToStart) {
                    break
                }
                assert !selection.noWorkReadyToStart // There should always be a node ready to start when executing sequentially
                def nextNode = selection.item
                assert !nextNode.isComplete()
                nodes << nextNode
                finalizedPlan.finishedExecuting(nextNode, null)
            }
            assert finalizedPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert finalizedPlan.allExecutionComplete()
        }
        return nodes
    }

    private TaskDependency brokenDependencies() {
        Mock(TaskDependency) {
            0 * getDependencies(_)
        }
    }

    private TaskInternal task(final String name) {
        task([:], name)
    }

    private TaskInternal task(Map options, final String name) {
        def task = createTask(name)
        relationships(options, task)
        if (options.failure) {
            failure(task, options.failure)
        }
        task.getDidWork() >> (options.containsKey('didWork') ? options.didWork : true)
        return task
    }

    private TaskInternal filteredTask(final String name) {
        def task = createTask(name)
        task.getTaskDependencies() >> brokenDependencies()
        task.getMustRunAfter() >> brokenDependencies()
        task.getShouldRunAfter() >> brokenDependencies()
        task.getFinalizedBy() >> taskDependencyResolvingTo(task, [])
        return task
    }

    private ScheduledWork scheduledWork(Node... nodes) {
        return new ScheduledWork(nodes as List<Node>, nodes as List<Node>)
    }
}
