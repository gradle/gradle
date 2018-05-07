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
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.tasks.TaskDestroyablesInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskLocalStateInternal
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.execution.TaskFailureHandler
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.Factories
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.ParallelismConfigurationManagerFixture
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DefaultTaskGraphExecuterSpec extends Specification {
    def cancellationToken = Mock(BuildCancellationToken)
    def project = ProjectBuilder.builder().build()
    def listenerManager = new DefaultListenerManager()
    def executer = Mock(TaskExecuter)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def coordinationService = new DefaultResourceLockCoordinationService()
    def parallelismConfiguration = new DefaultParallelismConfiguration(true, 1)
    def parallelismConfigurationManager = new ParallelismConfigurationManagerFixture(parallelismConfiguration)
    def workerLeases = new DefaultWorkerLeaseService(coordinationService, parallelismConfigurationManager)
    def executorFactory = Mock(ExecutorFactory)
    def taskExecuter = new DefaultTaskGraphExecuter(listenerManager, new DefaultTaskPlanExecutor(parallelismConfiguration, executorFactory, workerLeases, cancellationToken, coordinationService), Factories.constant(executer), buildOperationExecutor, workerLeases, coordinationService, Mock(GradleInternal))
    WorkerLeaseRegistry.WorkerLeaseCompletion parentWorkerLease
    def executedTasks = []

    def setup() {
        parentWorkerLease = workerLeases.getWorkerLease().start()
        _ * executorFactory.create(_) >> Mock(ManagedExecutor)
        _ * executer.execute(_, _, _) >> { args ->
            executedTasks << args[0]
        }
    }

    def cleanup() {
        parentWorkerLease.leaseFinish()
        workerLeases.stop()
    }

    def "notifies task listeners as tasks are executed"() {
        def listener = Mock(TaskExecutionListener)
        def a = task("a")
        def b = task("b")

        given:
        taskExecuter.addTaskExecutionListener(listener)
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute()

        then:
        1 * executorFactory.create(_) >> Mock(ManagedExecutor)
        1 * listener.beforeExecute(a)
        1 * listener.afterExecute(a, a.state)

        then:
        1 * listener.beforeExecute(b)
        1 * listener.afterExecute(b, b.state)
        0 * listener._

        and:
        buildOperationExecutor.operations[0].name == ":a"
        buildOperationExecutor.operations[0].displayName == "Task :a"
        buildOperationExecutor.operations[1].name == ":b"
        buildOperationExecutor.operations[1].displayName == "Task :b"
    }

    def "notifies task listener when task fails"() {
        def listener = Mock(TaskExecutionListener)
        def failure = new RuntimeException()
        def a = brokenTask("a", failure)

        given:
        taskExecuter.addTaskExecutionListener(listener)
        taskExecuter.addTasks([a])

        when:
        taskExecuter.execute()

        then:
        RuntimeException e = thrown()
        e == failure
        1 * listener.beforeExecute(a)
        1 * listener.afterExecute(a, a.state)
        0 * listener._
    }

    def "stops running tasks and fails with exception when build is cancelled"() {
        def a = task("a")
        def b = task("b")

        given:
        cancellationToken.cancellationRequested >>> [false, true]

        when:
        taskExecuter.addTasks([a, b])
        taskExecuter.execute()

        then:
        BuildCancelledException e = thrown()
        e.message == 'Build cancelled.'

        and:
        1 * executer.execute(a, a.state, _)
        0 * executer._
    }

    def "does not fail with exception when build is cancelled after last task has started"() {
        def a = task("a")
        def b = task("b")

        given:
        cancellationToken.cancellationRequested >>> [false, false, true]

        when:
        taskExecuter.addTasks([a, b])
        taskExecuter.execute()

        then:
        1 * executer.execute(a, a.state, _)
        1 * executer.execute(b, b.state, _)
        0 * executer._
    }

    def "does not fail with exception when build is cancelled and no tasks scheduled"() {
        given:
        cancellationToken.cancellationRequested >>> [true]

        when:
        taskExecuter.addTasks([])
        taskExecuter.execute()

        then:
        noExceptionThrown()
    }

    def "executes tasks in dependency order"() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c", b, a)
        Task d = task("d", c)

        when:
        taskExecuter.addTasks([d])
        taskExecuter.execute()

        then:
        executedTasks == [a, b, c, d]
    }

    def "executes dependencies in name order"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d", b, a, c)

        when:
        taskExecuter.addTasks([d])
        taskExecuter.execute()

        then:
        executedTasks == [a, b, c, d]
    }

    def "executes tasks in a single batch in name order"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")

        when:
        taskExecuter.addTasks([b, c, a])
        taskExecuter.execute()

        then:
        executedTasks == [a, b, c]
    }

    def "executes batches in order added"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d")

        when:
        taskExecuter.addTasks([c, b])
        taskExecuter.addTasks([d, a])
        taskExecuter.execute()

        then:
        executedTasks == [b, c, a, d]
    }

    def "executes shared dependencies of batches once only"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", a, b)
        Task d = task("d")
        Task e = task("e", b, d)

        when:
        taskExecuter.addTasks([c])
        taskExecuter.addTasks([e])
        taskExecuter.execute()

        then:
        executedTasks == [a, b, c, d, e]
    }

    def "adding tasks adds dependencies"() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c", b, a)
        Task d = task("d", c)

        when:
        taskExecuter.addTasks([d])

        then:
        taskExecuter.hasTask(":a")
        taskExecuter.hasTask(a)
        taskExecuter.hasTask(":b")
        taskExecuter.hasTask(b)
        taskExecuter.hasTask(":c")
        taskExecuter.hasTask(c)
        taskExecuter.hasTask(":d")
        taskExecuter.hasTask(d)
        taskExecuter.allTasks == [a, b, c, d]
    }

    def "get all tasks returns tasks in execution order"() {
        Task d = task("d")
        Task c = task("c")
        Task b = task("b", d, c)
        Task a = task("a", b)

        when:
        taskExecuter.addTasks([a])

        then:
        taskExecuter.allTasks == [c, d, b, a]
    }

    def "cannot use getter methods when graph has not been calculated"() {
        when:
        taskExecuter.hasTask(":a")

        then:
        def e = thrown(IllegalStateException)
        e.message == "Task information is not available, as this task execution graph has not been populated."

        when:
        taskExecuter.hasTask("a")

        then:
        e = thrown(IllegalStateException)
        e.message == "Task information is not available, as this task execution graph has not been populated."

        when:
        taskExecuter.getAllTasks()

        then:
        e = thrown(IllegalStateException)
        e.message == "Task information is not available, as this task execution graph has not been populated."
    }

    def "discards tasks after execute"() {
        Task a = task("a")
        Task b = task("b", a)

        when:
        taskExecuter.addTasks([b])
        taskExecuter.execute()

        then:
        !taskExecuter.hasTask(":a")
        !taskExecuter.hasTask(a)
        taskExecuter.allTasks.isEmpty()
    }

    def "can execute multiple times"() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c")

        when:
        taskExecuter.addTasks([b])
        taskExecuter.execute()

        then:
        executedTasks == [a, b]

        when:
        executedTasks.clear()
        taskExecuter.addTasks([c])

        then:
        taskExecuter.allTasks == [c]

        when:
        taskExecuter.execute()

        then:
        executedTasks == [c]
    }

    def "cannot add task with circular reference"() {
        Task a = newTask("a")
        Task b = task("b", a)
        Task c = task("c", b)
        addDependencies(a, c)

        when:
        taskExecuter.addTasks([c])
        taskExecuter.execute()

        then:
        thrown(CircularReferenceException)
    }

    def "notifies graph listener before execute"() {
        def taskPlanExecutor = Mock(TaskPlanExecutor)
        def taskExecuter = new DefaultTaskGraphExecuter(listenerManager, taskPlanExecutor, Factories.constant(executer), buildOperationExecutor, workerLeases, coordinationService, Mock(GradleInternal))
        TaskExecutionGraphListener listener = Mock(TaskExecutionGraphListener)
        Task a = task("a")

        when:
        taskExecuter.addTaskExecutionGraphListener(listener)
        taskExecuter.addTasks([a])
        taskExecuter.execute()

        then:
        1 * listener.graphPopulated(_)

        then:
        1 * taskPlanExecutor.process(_, _)
    }

    def "executes whenReady listener before execute"() {
        def taskPlanExecutor = Mock(TaskPlanExecutor)
        def taskExecuter = new DefaultTaskGraphExecuter(listenerManager, taskPlanExecutor, Factories.constant(executer), buildOperationExecutor, workerLeases, coordinationService, Mock(GradleInternal))
        def closure = Mock(Closure)
        def action = Mock(Action)
        Task a = task("a")

        when:
        taskExecuter.whenReady(closure)
        taskExecuter.whenReady(action)
        taskExecuter.addTasks([a])
        taskExecuter.execute()

        then:
        1 * closure.call()
        1 * action.execute(_)

        then:
        1 * taskPlanExecutor.process(_, _)
    }

    def "stops execution on first failure when no failure handler provided"() {
        final RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")

        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute()

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        executedTasks == [a]
    }

    def "stops execution on failure when failure handler indicates that execution should stop"() {
        final TaskFailureHandler handler = Mock(TaskFailureHandler)

        final RuntimeException failure = new RuntimeException()
        final RuntimeException wrappedFailure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")

        when:
        taskExecuter.useFailureHandler(handler)
        taskExecuter.addTasks([a, b])
        taskExecuter.execute()

        then:
        1 * handler.onTaskFailure(a) >> { args -> throw wrappedFailure }
        def e = thrown(RuntimeException)
        e == wrappedFailure

        and:
        executedTasks == [a]
    }

    def "notifies before task listener as tasks are executed"() {
        def closure = Mock(Closure) {
            _ * getMaximumNumberOfParameters() >> 1
        }
        def action = Mock(Action)

        final Task a = task("a")
        final Task b = task("b")

        when:
        taskExecuter.beforeTask(closure)
        taskExecuter.beforeTask(action)
        taskExecuter.addTasks([a, b])
        taskExecuter.execute()

        then:
        1 * closure.call(a)
        1 * closure.call(b)
        1 * action.execute(a)
        1 * action.execute(b)
    }

    def "notifies after task listener as tasks are executed"() {
        def closure = Mock(Closure) {
            _ * getMaximumNumberOfParameters() >> 1
        }
        def action = Mock(Action)

        final Task a = task("a")
        final Task b = task("b")

        when:
        taskExecuter.afterTask(closure)
        taskExecuter.afterTask(action)
        taskExecuter.addTasks([a, b])
        taskExecuter.execute()

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
            public boolean isSatisfiedBy(Task element) {
                return element != a
            }
        }

        when:
        taskExecuter.useFilter(spec)
        taskExecuter.addTasks([a, b])

        then:
        taskExecuter.allTasks == [b]

        when:
        taskExecuter.execute()

        then:
        executedTasks == [b]
    }

    def "does not execute filtered dependencies"() {
        final Task a = task("a", task("a-dep"))
        Task b = task("b")
        Task c = task("c", a, b)
        Spec<Task> spec = new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element != a
            }
        }

        when:
        taskExecuter.useFilter(spec)
        taskExecuter.addTasks([c])

        then:
        taskExecuter.allTasks == [b, c]

        when:
        taskExecuter.execute()

        then:
        executedTasks == [b, c]
    }

    def "will execute a task whose dependencies have been filtered on failure"() {
        final TaskFailureHandler handler = Mock(TaskFailureHandler)
        final RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b")
        final Task c = task("c", b)

        when:
        taskExecuter.useFailureHandler(handler)
        taskExecuter.useFilter(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element != b
            }
        })
        taskExecuter.addTasks([a, c])
        taskExecuter.execute()

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        executedTasks == [a, c]
    }

    def newTask(String name) {
        def mock = Mock(TaskInternal)
        _ * mock.name >> name
        _ * mock.identityPath >> project.identityPath.child(name)
        _ * mock.project >> project
        _ * mock.state >> Stub(TaskStateInternal) {
            getFailure() >> null
        }
        _ * mock.finalizedBy >> Stub(TaskDependency)
        _ * mock.mustRunAfter >> Stub(TaskDependency)
        _ * mock.shouldRunAfter >> Stub(TaskDependency)
        _ * mock.compareTo(_) >> { Task t -> name.compareTo(t.name) }
        _ * mock.outputs >> Stub(TaskOutputsInternal)
        _ * mock.inputs >> Stub(TaskInputsInternal)
        _ * mock.destroyables >> Stub(TaskDestroyablesInternal)
        _ * mock.localState >> Stub(TaskLocalStateInternal)
        _ * mock.path >> ":${name}"
        return mock
    }

    def task(String name, Task... dependsOn=[]) {
        def mock = newTask(name)
        addDependencies(mock, dependsOn)
        return mock
    }

    def addDependencies(Task task, Task... dependsOn) {
        _ * task.taskDependencies >> Stub(TaskDependency) {
            getDependencies(_) >> (dependsOn as Set)
        }
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
        _ * mock.compareTo(_) >> { Task t -> name.compareTo(t.name) }
        _ * mock.outputs >> Stub(TaskOutputsInternal)
        _ * mock.inputs >> Stub(TaskInputsInternal)
        _ * mock.destroyables >> Stub(TaskDestroyablesInternal)
        _ * mock.localState >> Stub(TaskLocalStateInternal)
        _ * mock.path >> ":${name}"
        return mock
    }
}
