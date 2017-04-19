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

package org.gradle.execution.taskgraph

import org.gradle.api.Action
import org.gradle.api.BuildCancelledException
import org.gradle.api.CircularReferenceException
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.execution.TaskFailureHandler
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.internal.resources.ResourceLockState
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TextUtil
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.util.TestUtil.createRootProject
import static org.gradle.util.TextUtil.toPlatformLineSeparators
import static org.gradle.util.WrapUtil.toList

public class DefaultTaskExecutionPlanTest extends AbstractProjectBuilderSpec {

    DefaultTaskExecutionPlan executionPlan
    ProjectInternal root;
    def cancellationHandler = Mock(BuildCancellationToken)
    def workerLeaseService = Mock(WorkerLeaseService)
    def coordinationService = Mock(ResourceLockCoordinationService)
    def parentWorkerLease = Mock(WorkerLeaseRegistry.WorkerLease)

    def setup() {
        root = createRootProject(temporaryFolder.testDirectory);
        executionPlan = new DefaultTaskExecutionPlan(cancellationHandler, coordinationService, workerLeaseService)
        _ * workerLeaseService.getProjectLock(_, _) >> Mock(ResourceLock) {
            _ * tryLock() >> true
        }
    }

    def "schedules tasks in dependency order"() {
        given:
        Task a = task("a");
        Task b = task("b", dependsOn: [a]);
        Task c = task("c", dependsOn: [b, a]);
        Task d = task("d", dependsOn: [c]);

        when:
        addToGraphAndPopulate([d])

        then:
        executes(a, b, c, d)
    }

    def "schedules task dependencies in name order when there are no dependencies between them"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");
        Task d = task("d", dependsOn: [b, a, c]);

        when:
        addToGraphAndPopulate([d])

        then:
        executes(a, b, c, d)
    }

    def "schedules a single batch of tasks in name order"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");

        when:
        addToGraphAndPopulate(toList(b, c, a));

        then:
        executes(a, b, c)
    }

    def "schedules separately added tasks in order added"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");
        Task d = task("d");

        when:
        executionPlan.addToTaskGraph(toList(c, b));
        executionPlan.addToTaskGraph(toList(d, a))
        executionPlan.determineExecutionPlan()

        then:
        executes(b, c, a, d)
    }

    @Unroll
    def "schedules #orderingRule task dependencies in name order"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c", (orderingRule): [b, a]);
        Task d = task("d", dependsOn: [b, a]);

        when:
        addToGraphAndPopulate([c, d]);

        then:
        executes(a, b, c, d)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "common tasks in separate batches are schedules only once"() {
        Task a = task("a");
        Task b = task("b");
        Task c = task("c", dependsOn: [a, b]);
        Task d = task("d");
        Task e = task("e", dependsOn: [b, d]);

        when:
        executionPlan.addToTaskGraph(toList(c));
        executionPlan.addToTaskGraph(toList(e));
        executionPlan.determineExecutionPlan();

        then:
        executes(a, b, c, d, e)
    }

    def "all dependencies scheduled when adding tasks"() {
        Task a = task("a");
        Task b = task("b", dependsOn: [a]);
        Task c = task("c", dependsOn: [b, a]);
        Task d = task("d", dependsOn: [c]);

        when:
        addToGraphAndPopulate(toList(d));

        then:
        executes(a, b, c, d)
    }

    @Unroll
    def "#orderingRule ordering is honoured for tasks added separately to graph"() {
        Task a = task("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", (orderingRule): [b])

        when:
        executionPlan.addToTaskGraph([c])
        executionPlan.addToTaskGraph([b])
        executionPlan.determineExecutionPlan()

        then:
        executes(a, b, c)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    @Unroll
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

        Task e = createTask("e")
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

    @Unroll
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
        executionPlan.useFilter(filter);
        addToGraphAndPopulate([finalized])

        then:
        executes(finalized)
    }

    def "finalizer tasks and their dependencies are not executed if finalized task did not run"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task finalizedDependency = task("finalizedDependency", failure: new RuntimeException("failure"))
        Task finalized = task("finalized", dependsOn: [finalizedDependency], finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executionPlan.tasks == [finalizedDependency, finalized, finalizerDependency, finalizer]
        executedTasks == [finalizedDependency]
    }

    def "finalizer tasks and their dependencies are executed if they are previously required even if the finalized task did not run"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task finalizedDependency = task("finalizedDependency", failure: new RuntimeException("failure"))
        Task finalized = task("finalized", dependsOn: [finalizedDependency], finalizedBy: [finalizer])
        executionPlan.useFailureHandler(createIgnoreTaskFailureHandler(finalizedDependency));

        when:
        addToGraphAndPopulate([finalizer, finalized])

        then:
        executionPlan.tasks == [finalizedDependency, finalized, finalizerDependency, finalizer]
        executedTasks == [finalizedDependency, finalizerDependency, finalizer]
    }

    def "finalizer tasks and their dependencies are executed if they are later required via dependency even if the finalized task did not do any work"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task dependsOnFinalizer = task("dependsOnFinalizer", dependsOn: [finalizer])
        Task finalized = task("finalized", finalizedBy: [finalizer], didWork: false)

        when:
        executionPlan.addToTaskGraph([finalized])
        executionPlan.addToTaskGraph([dependsOnFinalizer])
        executionPlan.determineExecutionPlan()

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
        Task d = task("d", dependsOn:[f2] )
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
        Task dep = task("dep", dependsOn:[f1] )
        Task f2 = task("f2", dependsOn: [dep])

        //2 finalized tasks
        Task finalized1 = task("finalized1", finalizedBy: [f1, f2])
        Task finalized2 = task("finalized2", finalizedBy: [f1, f2])

        //tasks that depends on finalized, we will execute them
        Task df1 = task("df1", dependsOn: [finalized1])
        Task df2 = task("df1", dependsOn: [finalized2])

        when:
        addToGraphAndPopulate([df1, df2])

        then:
        executes(finalized1, finalized2, f1, dep, f2, df1, df2)
    }

    @Unroll
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

    @Unroll
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
        orderingRule << ['dependsOn', 'mustRunAfter' , 'shouldRunAfter']
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
        executionPlan.addToTaskGraph([d])

        when:
        executionPlan.addToTaskGraph([a])
        executionPlan.determineExecutionPlan()

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
        Task d = createTask("d")
        Task e = task("e", dependsOn: [a, d])
        Task f = task("f", dependsOn: [e])
        Task g = task("g", dependsOn: [c, f])
        Task h = task("h", dependsOn: [b, g])
        relationships(d, shouldRunAfter: [g])

        when:
        addToGraphAndPopulate([e, h])

        then:
        executedTasks == [a, d, b, c, e, f, g, h]
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
        Task a = createTask("a")
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

    def "stops returning tasks on task execution failure"() {
        RuntimeException exception = new RuntimeException("failure");

        when:
        Task a = task([failure: exception],"a")
        Task b = task("b")
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == exception
    }

    def "stops returning tasks when build is cancelled"() {
        5 * cancellationHandler.cancellationRequested >>> [false, false, true, true, true]
        Task a = task("a");
        Task b = task("b");

        when:
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]

        when:
        executionPlan.awaitCompletion()

        then:
        BuildCancelledException e = thrown()
        e.message == 'Build cancelled.'
    }

    def "stops returning tasks on first task failure when no failure handler provided"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = task("a", failure: failure);
        Task b = task("b");

        when:
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "stops execution on task failure when failure handler indicates that execution should stop"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = task("a", failure: failure);
        Task b = task("b");

        addToGraphAndPopulate([a, b])

        TaskFailureHandler handler = Mock()
        RuntimeException wrappedFailure = new RuntimeException("wrapped");
        handler.onTaskFailure(a) >> {
            throw wrappedFailure
        }

        when:
        executionPlan.useFailureHandler(handler);

        then:
        executedTasks == [a]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == wrappedFailure
    }

    def "continues to return tasks and rethrows failure on completion when failure handler indicates that execution should continue"() {
        RuntimeException failure = new RuntimeException();
        Task a = task("a", failure: failure);
        Task b = task("b");
        addToGraphAndPopulate([a, b])

        when:
        executionPlan.useFailureHandler(createIgnoreTaskFailureHandler(a));

        then:
        executedTasks == [a, b]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure
    }

    @Unroll
    def "continues to return tasks when failure handler does not abort execution and tasks are #orderingRule dependent"() {
        RuntimeException failure = new RuntimeException();
        Task a = task("a", failure: failure);
        Task b = task("b", (orderingRule): [a]);
        addToGraphAndPopulate([a, b])

        when:
        executionPlan.useFailureHandler(createIgnoreTaskFailureHandler(a));

        then:
        executedTasks == [a, b]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "does not attempt to execute tasks whose dependencies failed to execute"() {
        RuntimeException failure = new RuntimeException()
        final Task a = task("a", failure: failure)
        final Task b = task("b", dependsOn: [a])
        final Task c = task("c")
        addToGraphAndPopulate([b, c])

        when:
        executionPlan.useFailureHandler(createIgnoreTaskFailureHandler(a))

        then:
        executedTasks == [a, c]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "clear removes all tasks"() {
        given:
        _ * coordinationService.withStateLock(_) >> { args ->
            args[0].transform(Mock(ResourceLockState))
            return true
        }
        Task a = task("a")

        when:
        addToGraphAndPopulate(toList(a))
        executionPlan.clear()

        then:
        executionPlan.tasks == []
        executedTasks == []
    }

    def "can add additional tasks after execution and clear"() {
        given:
        _ * coordinationService.withStateLock(_) >> { args ->
            args[0].transform(Mock(ResourceLockState))
            return true
        }
        Task a = task("a")
        Task b = task("b")

        when:
        addToGraphAndPopulate([a])

        then:
        executes(a)

        when:
        executionPlan.clear()
        addToGraphAndPopulate([b])

        then:
        executes(b)
    }

    def "does not build graph for or execute filtered tasks"() {
        given:
        Task a = filteredTask("a")
        Task b = task("b")
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != a }

        when:
        executionPlan.useFilter(filter);
        addToGraphAndPopulate([a, b])

        then:
        executes(b)
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
        executionPlan.useFilter(filter)
        addToGraphAndPopulate([c])

        then:
        executes(b, c)
    }

    @Unroll
    def "does not build graph for or execute filtered tasks reachable via #orderingRule task ordering"() {
        given:
        Task a = filteredTask("a")
        Task b = task("b", (orderingRule): [a])
        Task c = task("c", dependsOn: [a])
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != a }

        when:
        executionPlan.useFilter(filter)
        addToGraphAndPopulate([b, c])

        then:
        executes(b, c)

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
        executionPlan.useFilter(filter)
        addToGraphAndPopulate([c]);

        then:
        executes(c)
    }

    private void addToGraphAndPopulate(List tasks) {
        executionPlan.addToTaskGraph(tasks)
        executionPlan.determineExecutionPlan()
    }

    private TaskFailureHandler createIgnoreTaskFailureHandler(Task task) {
        Mock(TaskFailureHandler) {
            onTaskFailure(task) >> {}
        }
    }

    void executes(Task... expectedTasks) {
        assert executionPlan.tasks == expectedTasks as List
        assert expectedTasks == expectedTasks as List
    }

    def getExecutedTasks() {
        def tasks = []
        _ * parentWorkerLease.createChild() >> Mock(WorkerLeaseRegistry.WorkerLease) {
            _ * tryLock() >> true
        }
        _ * coordinationService.withStateLock(_) >> { args ->
            args[0].transform(Mock(ResourceLockState))
            return true
        }
        def moreTasks = true
        executionPlan.populateReadyTaskQueue()
        while (moreTasks) {
            moreTasks = executionPlan.executeWithTask(parentWorkerLease, new Action<TaskInfo>() {
                @Override
                void execute(TaskInfo taskInfo) {
                    tasks << taskInfo.task
                    executionPlan.taskComplete(taskInfo)
                }
            })
            executionPlan.populateReadyTaskQueue()
        }
        return tasks
    }

    private TaskDependency taskDependencyResolvingTo(TaskInternal task, List<Task> tasks) {
        Mock(TaskDependency) {
            getDependencies(task) >> tasks
        }
    }

    private TaskDependency brokenDependencies() {
        Mock(TaskDependency) {
            0 * getDependencies(_)
        }
    }

    private void dependsOn(TaskInternal task, List<Task> dependsOnTasks) {
        task.getTaskDependencies() >> taskDependencyResolvingTo(task, dependsOnTasks)
    }

    private void mustRunAfter(TaskInternal task, List<Task> mustRunAfterTasks) {
        task.getMustRunAfter() >> taskDependencyResolvingTo(task, mustRunAfterTasks)
    }

    private void finalizedBy(TaskInternal task, List<Task> finalizedByTasks) {
        task.getFinalizedBy() >> taskDependencyResolvingTo(task, finalizedByTasks)
    }

    private void shouldRunAfter(TaskInternal task, List<Task> shouldRunAfterTasks) {
        task.getShouldRunAfter() >> taskDependencyResolvingTo(task, shouldRunAfterTasks)
    }

    private void failure(TaskInternal task, final RuntimeException failure) {
        task.state.getFailure() >> failure
        task.state.rethrowFailure() >> { throw failure }
    }

    private TaskInternal task(final String name) {
        task([:], name)
    }

    private TaskOutputsInternal emptyTaskOutputs() {
        Mock(TaskOutputsInternal) {
            getFiles() >> root.files()
        }
    }

    private TaskInternal task(Map options, final String name) {
        def task = createTask(name)
        relationships(options, task)
        if (options.failure) {
            failure(task, options.failure)
        }
        task.getDidWork() >> (options.containsKey('didWork') ? options.didWork : true)
        task.getOutputs() >> emptyTaskOutputs()
        return task
    }

    private void relationships(Map options, TaskInternal task) {
        dependsOn(task, options.dependsOn ?: [])
        mustRunAfter(task, options.mustRunAfter ?: [])
        shouldRunAfter(task, options.shouldRunAfter ?: [])
        finalizedBy(task, options.finalizedBy ?: [])
    }

    private TaskInternal filteredTask(final String name) {
        def task = createTask(name);
        task.getTaskDependencies() >> brokenDependencies()
        task.getMustRunAfter() >> brokenDependencies()
        task.getShouldRunAfter() >> brokenDependencies()
        task.getFinalizedBy() >> taskDependencyResolvingTo(task, [])
        return task
    }

    private TaskInternal createTask(final String name) {
        TaskInternal task = Mock()
        TaskStateInternal state = Mock()
        task.getProject() >> root
        task.name >> name
        task.path >> ':' + name
        task.state >> state
        task.toString() >> "task $name"
        task.compareTo(_ as TaskInternal) >> { TaskInternal taskInternal ->
            return name.compareTo(taskInternal.getName());
        }
        task.getOutputs() >> emptyTaskOutputs()
        return task;
    }
}

