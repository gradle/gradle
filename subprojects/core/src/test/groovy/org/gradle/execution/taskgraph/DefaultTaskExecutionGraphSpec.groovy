/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.BuildScopeListenerRegistrationListener
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.internal.tasks.TaskDestroyablesInternal
import org.gradle.api.internal.tasks.TaskLocalStateInternal
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Spec
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.configuration.internal.TestListenerBuildOperationDecorator
import org.gradle.execution.plan.AbstractExecutionPlanSpec
import org.gradle.execution.plan.DefaultExecutionPlan
import org.gradle.execution.plan.DefaultPlanExecutor
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies
import org.gradle.execution.plan.ExecutionNodeAccessHierarchy
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeExecutor
import org.gradle.execution.plan.OrdinalGroupFactory
import org.gradle.execution.plan.PlanExecutor
import org.gradle.execution.plan.SelfExecutingNode
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.execution.plan.TaskNodeDependencyResolver
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.file.Stat
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.util.Path

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class DefaultTaskExecutionGraphSpec extends AbstractExecutionPlanSpec {
    def cancellationToken = Mock(BuildCancellationToken)
    def listenerManager = new DefaultListenerManager(Scopes.Build)
    def graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class)
    def taskExecutionListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class)
    def listenerRegistrationListener = listenerManager.getBroadcaster(BuildScopeListenerRegistrationListener.class)
    def nodeExecutor = Mock(NodeExecutor)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def listenerBuildOperationDecorator = new TestListenerBuildOperationDecorator()
    def parallelismConfiguration = new DefaultParallelismConfiguration(true, 1)
    def workerLeases = new DefaultWorkerLeaseService(coordinator, parallelismConfiguration)
    def executorFactory = Mock(ExecutorFactory)
    def accessHierarchies = new ExecutionNodeAccessHierarchies(CASE_SENSITIVE, Stub(Stat))
    def taskNodeFactory = new TaskNodeFactory(thisBuild, Stub(BuildTreeWorkGraphController), nodeValidator, new TestBuildOperationExecutor(), accessHierarchies)
    def dependencyResolver = new TaskDependencyResolver([new TaskNodeDependencyResolver(taskNodeFactory)])
    def projectStateRegistry = Stub(ProjectStateRegistry)
    def executionPlan = newExecutionPlan()
    def taskGraph = new DefaultTaskExecutionGraph(
        new DefaultPlanExecutor(parallelismConfiguration, executorFactory, workerLeases, cancellationToken, coordinator, new DefaultInternalOptions([:])),
        [nodeExecutor],
        buildOperationExecutor,
        listenerBuildOperationDecorator,
        thisBuild,
        graphListeners,
        taskExecutionListeners,
        listenerRegistrationListener,
        Stub(ServiceRegistry) {
            get(TaskDependencyFactory) >> TestFiles.taskDependencyFactory()
        }
    )
    WorkerLeaseRegistry.WorkerLeaseCompletion parentWorkerLease
    def executedTasks = []
    def failures = []

    def setup() {
        parentWorkerLease = workerLeases.startWorker()
        _ * executorFactory.create(_) >> Mock(ManagedExecutor)
        _ * nodeExecutor.execute(_ as Node, _ as NodeExecutionContext) >> { Node node, NodeExecutionContext context ->
            if (node instanceof LocalTaskNode) {
                executedTasks << node.task
                return true
            } else if (node instanceof SelfExecutingNode) {
                return true
            } else {
                return false
            }
        }
        _ * projectStateRegistry.withMutableStateOfAllProjects(_) >> { Runnable r -> r.run() }
    }

    def cleanup() {
        parentWorkerLease.leaseFinish()
        workerLeases.stop()
    }

    def "collects task failures"() {
        def failure = new RuntimeException()
        def a = brokenTask("a", failure)

        given:
        def finalizedPlan = populate([a])

        when:
        def result = taskGraph.execute(finalizedPlan)

        then:
        result.failures == [failure]
    }

    def "stops running nodes and fails with exception when build is cancelled"() {
        def a = task("a")
        def b = task("b")

        given:
        cancellationToken.cancellationRequested >>> [false, false, true]

        when:
        populateAndExecute([a, b])

        then:
        failures.size() == 1
        failures[0] instanceof BuildCancelledException
        executedTasks == [a]
    }

    def "does not fail with exception when build is cancelled after last node has started"() {
        def a = task("a")
        def b = task("b")

        given:
        cancellationToken.cancellationRequested >>> [false, false, false, false, true]

        when:
        populateAndExecute([a, b])

        then:
        failures.empty
        executedTasks == [a, b]
    }

    def "does not fail with exception when build is cancelled and no tasks scheduled"() {
        given:
        cancellationToken.cancellationRequested >>> [true]

        when:
        populateAndExecute([])

        then:
        failures.empty
    }

    def "executes tasks in dependency order"() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c", b, a)
        Task d = task("d", c)

        when:
        populateAndExecute([d])

        then:
        executedTasks == [a, b, c, d]
        failures.empty
    }

    def "executes dependencies in name order"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d", b, a, c)

        when:
        populateAndExecute([d])

        then:
        executedTasks == [a, b, c, d]
        failures.empty
    }

    def "executes tasks in a single batch in name order"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")

        when:
        populateAndExecute([b, c, a])

        then:
        executedTasks == [a, b, c]
        failures.empty
    }

    def "executes batches in order added"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d")

        when:
        executionPlan.addEntryTasks([c, b])
        executionPlan.addEntryTasks([d, a])
        execute()

        then:
        executedTasks == [b, c, a, d]
        failures.empty
    }

    def "executes shared dependencies of batches once only"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", a, b)
        Task d = task("d")
        Task e = task("e", b, d)

        when:
        executionPlan.addEntryTasks([c])
        executionPlan.addEntryTasks([e])
        execute()

        then:
        executedTasks == [a, b, c, d, e]
        failures.empty
    }

    def "adding tasks adds dependencies"() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c", b, a)
        Task d = task("d", c)

        when:
        populate([d])

        then:
        taskGraph.hasTask(":a")
        taskGraph.hasTask(a)
        taskGraph.hasTask(":b")
        taskGraph.hasTask(b)
        taskGraph.hasTask(":c")
        taskGraph.hasTask(c)
        taskGraph.hasTask(":d")
        taskGraph.hasTask(d)
        taskGraph.allTasks == [a, b, c, d]
    }

    def "get all tasks returns tasks in execution order"() {
        Task d = task("d")
        Task c = task("c")
        Task b = task("b", d, c)
        Task a = task("a", b)

        when:
        populate([a])

        then:
        taskGraph.allTasks == [c, d, b, a]
    }

    def "is empty when no tasks have been added"() {
        expect:
        !taskGraph.hasTask(":a")
        taskGraph.allTasks.empty
    }

    def "retains all tasks list after execute until next execution"() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c")

        when:
        def finalizedPlan = populate([b])
        taskGraph.allTasks
        taskGraph.execute(finalizedPlan)

        then:
        // tests existing behaviour, not desired behaviour
        !taskGraph.hasTask(":a")
        !taskGraph.hasTask(a)
        taskGraph.allTasks == [a, b]

        when:
        def plan2 = newExecutionPlan()
        plan2.addEntryTasks([c])
        plan2.determineExecutionPlan()
        def finalizedPlan2 = plan2.finalizePlan()
        taskGraph.populate(finalizedPlan2)

        then:
        !taskGraph.hasTask(":a")
        !taskGraph.hasTask(a)
        taskGraph.allTasks == [c]
    }

    def "can execute multiple times"() {
        Task a = brokenTask("a", new RuntimeException())
        Task b = task("b", a)
        Task c = task("c")

        when:
        populateAndExecute([b])

        then:
        executedTasks == [a]
        failures.size() == 1

        when:
        def plan2 = newExecutionPlan()
        plan2.addEntryTasks([c])
        plan2.determineExecutionPlan()
        def finalizedPlan2 = plan2.finalizePlan()
        taskGraph.populate(finalizedPlan2)

        then:
        taskGraph.allTasks == [c]

        when:
        executedTasks.clear()
        def result2 = taskGraph.execute(finalizedPlan2)

        then:
        executedTasks == [c]
        result2.failures.empty
    }

    def "cannot add task with circular reference"() {
        Task a = createTask("a")
        Task b = task("b", a)
        Task c = task("c", b)
        addDependencies(a, c)

        when:
        populateAndExecute([c])

        then:
        thrown(CircularReferenceException)
    }

    def "notifies graph listener before first execute"() {
        def planExecutor = Mock(PlanExecutor)
        def taskGraph = new DefaultTaskExecutionGraph(
            planExecutor,
            [nodeExecutor],
            buildOperationExecutor,
            listenerBuildOperationDecorator,
            thisBuild,
            graphListeners,
            taskExecutionListeners,
            listenerRegistrationListener,
            Stub(ServiceRegistry)
        )
        TaskExecutionGraphListener listener = Mock(TaskExecutionGraphListener)

        when:
        taskGraph.addTaskExecutionGraphListener(listener)
        def finalizedPlan = Stub(FinalizedExecutionPlan)
        taskGraph.populate(finalizedPlan)
        taskGraph.execute(finalizedPlan)

        then:
        1 * listener.graphPopulated(_)

        then:
        1 * planExecutor.process(_, _)

        when:
        taskGraph.populate(finalizedPlan)
        taskGraph.execute(finalizedPlan)

        then:
        0 * listener._
    }

    def "executes whenReady listener before first execute"() {
        def planExecutor = Mock(PlanExecutor)
        def taskGraph = new DefaultTaskExecutionGraph(
            planExecutor,
            [nodeExecutor],
            buildOperationExecutor,
            listenerBuildOperationDecorator,
            thisBuild,
            graphListeners,
            taskExecutionListeners,
            listenerRegistrationListener,
            Stub(ServiceRegistry)
        )
        def closure = Mock(Closure)
        def action = Mock(Action)
        Task a = task("a")

        when:
        taskGraph.whenReady(closure)
        taskGraph.whenReady(action)
        def finalizedPlan = Stub(FinalizedExecutionPlan)
        taskGraph.populate(finalizedPlan)
        taskGraph.execute(finalizedPlan)

        then:
        1 * closure.call()
        1 * action.execute(_)

        then:
        1 * planExecutor.process(_, _)

        and:
        with(buildOperationExecutor.operations[0]) {
            name == 'Notify task graph whenReady listeners'
            displayName == 'Notify task graph whenReady listeners'
            details.buildPath == ':'
        }

        when:
        def finalizedPlan2 = Stub(FinalizedExecutionPlan)
        taskGraph.populate(finalizedPlan2)
        taskGraph.execute(finalizedPlan2)

        then:
        0 * closure._
        0 * action._
    }

    def "stops execution on first failure when no failure handler provided"() {
        final RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")

        when:
        populateAndExecute([a, b])

        then:
        executedTasks == [a]
        failures == [failure]
    }

    def "stops execution on failure when failure handler indicates that execution should stop"() {
        final RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")

        when:
        populateAndExecute([a, b])

        then:
        executedTasks == [a]
        failures == [failure]
    }

    def "notifies before task listeners"() {
        def closure = Mock(Closure) {
            _ * getMaximumNumberOfParameters() >> 1
        }
        def action = Mock(Action)

        final Task a = task("a")
        final Task b = task("b")

        when:
        taskGraph.beforeTask(closure)
        taskGraph.beforeTask(action)
        taskExecutionListeners.source.beforeExecute(a)
        taskExecutionListeners.source.beforeExecute(b)

        then:
        1 * closure.call(a)
        1 * closure.call(b)
        1 * action.execute(a)
        1 * action.execute(b)
    }

    def "notifies after task listeners"() {
        def closure = Mock(Closure) {
            _ * getMaximumNumberOfParameters() >> 1
        }
        def action = Mock(Action)

        final Task a = task("a")
        final Task b = task("b")

        when:
        taskGraph.afterTask(closure)
        taskGraph.afterTask(action)
        taskExecutionListeners.source.afterExecute(a, a.state)
        taskExecutionListeners.source.afterExecute(b, b.state)

        then:
        1 * closure.call(a)
        1 * closure.call(b)
        1 * action.execute(a)
        1 * action.execute(b)
    }

    def "does not execute filtered tasks"() {
        final Task a = task("a", task("a-dep"))
        Task b = task("b")
        Spec<Task> spec = new Spec<Task>() {
            boolean isSatisfiedBy(Task element) {
                return element != a
            }
        }

        when:
        executionPlan.addFilter(spec)
        def finalizedPlan = populate([a, b])

        then:
        taskGraph.allTasks == [b]

        when:
        def result = taskGraph.execute(finalizedPlan)

        then:
        executedTasks == [b]
        result.failures.empty
    }

    def "does not execute filtered dependencies"() {
        final Task a = task("a", task("a-dep"))
        Task b = task("b")
        Task c = task("c", a, b)
        Spec<Task> spec = new Spec<Task>() {
            boolean isSatisfiedBy(Task element) {
                return element != a
            }
        }

        when:
        executionPlan.addFilter(spec)
        def finalizedPlan = populate([c])

        then:
        taskGraph.allTasks == [b, c]

        when:
        def result = taskGraph.execute(finalizedPlan)

        then:
        executedTasks == [b, c]
        result.failures.empty
    }

    def "will execute a task whose dependencies have been filtered on failure"() {
        final RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")
        final Task c = task("c", b)

        when:
        executionPlan.continueOnFailure = true
        executionPlan.addFilter(new Spec<Task>() {
            boolean isSatisfiedBy(Task element) {
                return element != b
            }
        })
        populateAndExecute([a, c])

        then:
        executedTasks == [a, c]
        failures == [failure]
    }

    private FinalizedExecutionPlan populate(List<Task> tasks) {
        executionPlan.addEntryTasks(tasks)
        executionPlan.determineExecutionPlan()
        def finalizedPlan = executionPlan.finalizePlan()
        taskGraph.populate(finalizedPlan)
        return finalizedPlan
    }

    void populateAndExecute(List<Task> tasks) {
        def finalizedPlan = populate(tasks)
        executedTasks.clear()
        failures.clear()
        def result = taskGraph.execute(finalizedPlan)
        failures.addAll(result.failures)
    }

    void execute() {
        executionPlan.determineExecutionPlan()
        def finalizedPlan = executionPlan.finalizePlan()
        taskGraph.populate(finalizedPlan)
        executedTasks.clear()
        failures.clear()
        def result = taskGraph.execute(finalizedPlan)
        failures.addAll(result.failures)
    }

    private DefaultExecutionPlan newExecutionPlan() {
        return new DefaultExecutionPlan(Path.ROOT.toString(), taskNodeFactory, new OrdinalGroupFactory(), dependencyResolver, new ExecutionNodeAccessHierarchy(CASE_SENSITIVE, Stub(Stat)), new ExecutionNodeAccessHierarchy(CASE_SENSITIVE, Stub(Stat)), coordinator)
    }

    def task(String name, Task... dependsOn = []) {
        def mock = createTask(name)
        addDependencies(mock, dependsOn)
        return mock
    }

    def addDependencies(Task task, Task... dependsOn) {
        _ * task.taskDependencies >> taskDependencyResolvingTo(task, dependsOn as List)
        _ * task.lifecycleDependencies >> taskDependencyResolvingTo(task, dependsOn as List)
        _ * task.finalizedBy >> taskDependencyResolvingTo(task, [])
        _ * task.shouldRunAfter >> taskDependencyResolvingTo(task, [])
        _ * task.mustRunAfter >> taskDependencyResolvingTo(task, [])
        _ * task.sharedResources >> []
    }

    def brokenTask(String name, RuntimeException failure) {
        def mock = Mock(TaskInternal)
        _ * mock.name >> name
        _ * mock.identityPath >> project.identityPath.child(name)
        _ * mock.project >> project
        _ * mock.state >> Stub(TaskStateInternal) {
            getFailure() >> failure
            rethrowFailure() >> { throw failure }
        }
        _ * mock.taskDependencies >> Stub(TaskDependencyInternal)
        _ * mock.lifecycleDependencies >> Stub(TaskDependencyInternal)
        _ * mock.finalizedBy >> Stub(TaskDependencyInternal)
        _ * mock.mustRunAfter >> Stub(TaskDependencyInternal)
        _ * mock.shouldRunAfter >> Stub(TaskDependencyInternal)
        _ * mock.sharedResources >> []
        _ * mock.compareTo(_) >> { Task t -> name.compareTo(t.name) }
        _ * mock.outputs >> Stub(TaskOutputsInternal)
        _ * mock.inputs >> Stub(TaskInputsInternal)
        _ * mock.destroyables >> Stub(TaskDestroyablesInternal)
        _ * mock.localState >> Stub(TaskLocalStateInternal)
        _ * mock.path >> ":${name}"
        _ * mock.taskIdentity >> TestTaskIdentities.create(name, DefaultTask, project as ProjectInternal)
        return mock
    }
}
