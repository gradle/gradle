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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.project.taskfactory.TaskIdentity
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDestroyablesInternal
import org.gradle.api.internal.tasks.TaskLocalStateInternal
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.composite.internal.IncludedBuildTaskGraph
import org.gradle.configuration.internal.TestListenerBuildOperationDecorator
import org.gradle.execution.TaskSelector
import org.gradle.execution.plan.AbstractExecutionPlanSpec
import org.gradle.execution.plan.ConsumedAndProducedLocations
import org.gradle.execution.plan.DefaultExecutionPlan
import org.gradle.execution.plan.DefaultPlanExecutor
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeExecutor
import org.gradle.execution.plan.PlanExecutor
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.execution.plan.TaskNodeDependencyResolver
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.util.Path

class DefaultTaskExecutionGraphSpec extends AbstractExecutionPlanSpec {
    def cancellationToken = Mock(BuildCancellationToken)
    def listenerManager = new DefaultListenerManager(Scopes.Build)
    def graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class)
    def taskExecutionListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class)
    def listenerRegistrationListener = listenerManager.getBroadcaster(BuildScopeListenerRegistrationListener.class)
    def nodeExecutor = Mock(NodeExecutor)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def listenerBuildOperationDecorator = new TestListenerBuildOperationDecorator()
    def coordinationService = new DefaultResourceLockCoordinationService()
    def parallelismConfiguration = new DefaultParallelismConfiguration(true, 1)
    def workerLeases = new DefaultWorkerLeaseService(coordinationService, parallelismConfiguration)
    def executorFactory = Mock(ExecutorFactory)
    def taskNodeFactory = new TaskNodeFactory(thisBuild, Stub(IncludedBuildTaskGraph))
    def dependencyResolver = new TaskDependencyResolver([new TaskNodeDependencyResolver(taskNodeFactory)])
    def projectStateRegistry = Stub(ProjectStateRegistry)
    def executionPlan = new DefaultExecutionPlan(Path.ROOT.toString(), taskNodeFactory, dependencyResolver, new ConsumedAndProducedLocations())
    def taskGraph = new DefaultTaskExecutionGraph(
        new DefaultPlanExecutor(parallelismConfiguration, executorFactory, workerLeases, cancellationToken, coordinationService),
        [nodeExecutor],
        buildOperationExecutor,
        listenerBuildOperationDecorator,
        coordinationService,
        thisBuild,
        executionPlan,
        graphListeners,
        taskExecutionListeners,
        listenerRegistrationListener,
        projectStateRegistry,
        Stub(ServiceRegistry),
        Stub(TaskSelector)
    )
    WorkerLeaseRegistry.WorkerLeaseCompletion parentWorkerLease
    def executedTasks = []
    def failures = []

    def setup() {
        parentWorkerLease = workerLeases.getWorkerLease().start()
        _ * executorFactory.create(_) >> Mock(ManagedExecutor)
        _ * nodeExecutor.execute(_ as Node, _ as NodeExecutionContext) >> { Node node, NodeExecutionContext context ->
            if (node instanceof LocalTaskNode) {
                executedTasks << node.task
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
        taskGraph.addEntryTasks([a])
        taskGraph.populate()

        when:
        taskGraph.execute(failures)

        then:
        failures == [failure]
    }

    def "stops running tasks and fails with exception when build is cancelled"() {
        def a = task("a")
        def b = task("b")

        given:
        cancellationToken.cancellationRequested >>> [false, true]

        when:
        taskGraph.addEntryTasks([a, b])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        failures.size() == 1
        failures[0] instanceof BuildCancelledException
        executedTasks == [a]
    }

    def "does not fail with exception when build is cancelled after last task has started"() {
        def a = task("a")
        def b = task("b")

        given:
        cancellationToken.cancellationRequested >>> [false, false, true]

        when:
        taskGraph.addEntryTasks([a, b])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        failures.empty
        executedTasks == [a, b]
    }

    def "does not fail with exception when build is cancelled and no tasks scheduled"() {
        given:
        cancellationToken.cancellationRequested >>> [true]

        when:
        taskGraph.addEntryTasks([])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        failures.empty
    }

    def "executes tasks in dependency order"() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c", b, a)
        Task d = task("d", c)

        when:
        taskGraph.addEntryTasks([d])
        taskGraph.populate()
        taskGraph.execute(failures)

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
        taskGraph.addEntryTasks([d])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        executedTasks == [a, b, c, d]
        failures.empty
    }

    def "executes tasks in a single batch in name order"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")

        when:
        taskGraph.addEntryTasks([b, c, a])
        taskGraph.populate()
        taskGraph.execute(failures)

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
        taskGraph.addEntryTasks([c, b])
        taskGraph.addEntryTasks([d, a])
        taskGraph.populate()
        taskGraph.execute(failures)

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
        taskGraph.addEntryTasks([c])
        taskGraph.addEntryTasks([e])
        taskGraph.populate()
        taskGraph.execute(failures)

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
        taskGraph.addEntryTasks([d])

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
        taskGraph.addEntryTasks([a])

        then:
        taskGraph.allTasks == [c, d, b, a]
    }

    def "cannot use getter methods when graph has not been calculated"() {
        when:
        taskGraph.hasTask(":a")

        then:
        def e = thrown(IllegalStateException)
        e.message == "Task information is not available, as this task execution graph has not been populated."

        when:
        taskGraph.hasTask("a")

        then:
        e = thrown(IllegalStateException)
        e.message == "Task information is not available, as this task execution graph has not been populated."

        when:
        taskGraph.getAllTasks()

        then:
        e = thrown(IllegalStateException)
        e.message == "Task information is not available, as this task execution graph has not been populated."
    }

    def "discards tasks after execute"() {
        Task a = task("a")
        Task b = task("b", a)

        when:
        taskGraph.addEntryTasks([b])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        !taskGraph.hasTask(":a")
        !taskGraph.hasTask(a)
        taskGraph.allTasks.isEmpty()
    }

    def "can execute multiple times"() {
        Task a = brokenTask("a", new RuntimeException())
        Task b = task("b", a)
        Task c = task("c")

        when:
        taskGraph.addEntryTasks([b])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        executedTasks == [a]
        failures.size() == 1

        when:
        def failures2 = []
        executedTasks.clear()
        taskGraph.addEntryTasks([c])

        then:
        taskGraph.allTasks == [c]

        when:
        taskGraph.execute(failures2)

        then:
        executedTasks == [c]
        failures2.empty
    }

    def "cannot add task with circular reference"() {
        Task a = createTask("a")
        Task b = task("b", a)
        Task c = task("c", b)
        addDependencies(a, c)

        when:
        taskGraph.addEntryTasks([c])
        taskGraph.populate()
        taskGraph.execute(failures)

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
            coordinationService,
            thisBuild,
            executionPlan,
            graphListeners,
            taskExecutionListeners,
            listenerRegistrationListener,
            projectStateRegistry,
            Stub(ServiceRegistry),
            Stub(TaskSelector)
        )
        TaskExecutionGraphListener listener = Mock(TaskExecutionGraphListener)
        Task a = task("a")

        when:
        taskGraph.addTaskExecutionGraphListener(listener)
        taskGraph.addEntryTasks([a])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        1 * listener.graphPopulated(_)

        then:
        1 * planExecutor.process(_, _, _)

        when:
        taskGraph.execute(failures)

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
            coordinationService,
            thisBuild,
            executionPlan,
            graphListeners,
            taskExecutionListeners,
            listenerRegistrationListener,
            projectStateRegistry,
            Stub(ServiceRegistry),
            Stub(TaskSelector)
        )
        def closure = Mock(Closure)
        def action = Mock(Action)
        Task a = task("a")

        when:
        taskGraph.whenReady(closure)
        taskGraph.whenReady(action)
        taskGraph.addEntryTasks([a])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        1 * closure.call()
        1 * action.execute(_)

        then:
        1 * planExecutor.process(_, _, _)

        and:
        with(buildOperationExecutor.operations[0]) {
            name == 'Notify task graph whenReady listeners'
            displayName == 'Notify task graph whenReady listeners'
            details.buildPath == ':'
        }

        when:
        taskGraph.execute(failures)

        then:
        0 * closure._
        0 * action._
    }

    def "stops execution on first failure when no failure handler provided"() {
        final RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")

        taskGraph.addEntryTasks([a, b])
        taskGraph.populate()

        when:
        taskGraph.execute(failures)

        then:
        executedTasks == [a]
        failures == [failure]
    }

    def "stops execution on failure when failure handler indicates that execution should stop"() {
        final RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")

        when:
        taskGraph.addEntryTasks([a, b])
        taskGraph.populate()
        taskGraph.execute(failures)

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
        taskGraph.useFilter(spec)
        taskGraph.addEntryTasks([a, b])
        taskGraph.populate()

        then:
        taskGraph.allTasks == [b]

        when:
        taskGraph.execute(failures)

        then:
        executedTasks == [b]
        failures.empty
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
        taskGraph.useFilter(spec)
        taskGraph.addEntryTasks([c])
        taskGraph.populate()

        then:
        taskGraph.allTasks == [b, c]

        when:
        taskGraph.execute(failures)

        then:
        executedTasks == [b, c]
        failures.empty
    }

    def "will execute a task whose dependencies have been filtered on failure"() {
        final RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")
        final Task c = task("c", b)

        when:
        taskGraph.continueOnFailure = true
        taskGraph.useFilter(new Spec<Task>() {
            boolean isSatisfiedBy(Task element) {
                return element != b
            }
        })
        taskGraph.addEntryTasks([a, c])
        taskGraph.populate()
        taskGraph.execute(failures)

        then:
        executedTasks == [a, c]
        failures == [failure]
    }

    def task(String name, Task... dependsOn = []) {
        def mock = createTask(name)
        addDependencies(mock, dependsOn)
        return mock
    }

    def addDependencies(Task task, Task... dependsOn) {
        _ * task.taskDependencies >> taskDependencyResolvingTo(task, dependsOn as List)
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
        _ * mock.taskDependencies >> Stub(TaskDependency)
        _ * mock.finalizedBy >> Stub(TaskDependency)
        _ * mock.mustRunAfter >> Stub(TaskDependency)
        _ * mock.shouldRunAfter >> Stub(TaskDependency)
        _ * mock.sharedResources >> []
        _ * mock.compareTo(_) >> { Task t -> name.compareTo(t.name) }
        _ * mock.outputs >> Stub(TaskOutputsInternal)
        _ * mock.inputs >> Stub(TaskInputsInternal)
        _ * mock.destroyables >> Stub(TaskDestroyablesInternal)
        _ * mock.localState >> Stub(TaskLocalStateInternal)
        _ * mock.path >> ":${name}"
        _ * mock.taskIdentity >> TaskIdentity.create(name, DefaultTask, project as ProjectInternal)
        return mock
    }
}
