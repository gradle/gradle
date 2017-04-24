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

import org.gradle.api.BuildCancelledException
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.execution.internal.InternalTaskExecutionListener
import org.gradle.api.execution.internal.TaskOperationInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.Factories
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.StoppableExecutor
import org.gradle.internal.event.DefaultListenerManager

import org.gradle.internal.progress.TestBuildOperationExecutor
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
    def workerLeases = new DefaultWorkerLeaseService(coordinationService, true, 1)
    def executorFactory = Mock(ExecutorFactory)
    def threadExecutor = Mock(StoppableExecutor)
    def taskExecuter = new DefaultTaskGraphExecuter(listenerManager, new DefaultTaskPlanExecutor(1, executorFactory, workerLeases), Factories.constant(executer), cancellationToken, buildOperationExecutor, workerLeases, coordinationService)
    WorkerLeaseRegistry.WorkerLeaseCompletion parentWorkerLease
    Thread taskReadyPopulator

    def setup() {
        parentWorkerLease = workerLeases.getWorkerLease().start()
        1 * executorFactory.create(_) >> threadExecutor
        1 * threadExecutor.execute(_ as Runnable) >> { args ->
            taskReadyPopulator = new Thread(args[0])
            taskReadyPopulator.start()
        }
    }

    def cleanup() {
        parentWorkerLease.leaseFinish()
        workerLeases.stop()
    }

    def "notifies task listeners as tasks are executed"() {
        def listener = Mock(TaskExecutionListener)
        def legacyListener = Mock(InternalTaskExecutionListener)
        def a = task("a")
        def b = task("b")

        given:
        taskExecuter.addTaskExecutionListener(listener)
        listenerManager.addListener(legacyListener)
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute()

        then:
        1 * legacyListener.beforeExecute(_, _) >> { TaskOperationInternal t, e ->
            assert t.task == a
        }
        1 * listener.beforeExecute(a)
        1 * listener.afterExecute(a, a.state)
        1 * legacyListener.afterExecute(_, _)

        then:
        1 * legacyListener.beforeExecute(_, _) >> { TaskOperationInternal t, e ->
            assert t.task == b
        }
        1 * listener.beforeExecute(b)
        1 * listener.afterExecute(b, b.state)
        1 * legacyListener.afterExecute(_, _)
        0 * listener._
        0 * legacyListener._

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
        def canceled = false
        def a = task("a")
        def b = task("b")

        given:
        cancellationToken.cancellationRequested >> { args -> canceled }

        when:
        taskExecuter.addTasks([a, b])
        taskExecuter.execute()

        then:
        BuildCancelledException e = thrown()
        e.message == 'Build cancelled.'

        and:
        1 * executer.execute(a, a.state, _) >> { args -> canceled = true }
        0 * executer._
    }

    def "does not fail with exception when build is cancelled after last task has started"() {
        def canceled = false
        def a = task("a")
        def b = task("b")

        given:
        cancellationToken.cancellationRequested >> { args -> canceled }

        when:
        taskExecuter.addTasks([a, b])
        taskExecuter.execute()

        then:
        1 * executer.execute(a, a.state, _)
        1 * executer.execute(b, b.state, _) >> { args -> canceled = true }
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

    def task(String name) {
        def mock = Mock(TaskInternal)
        _ * mock.name >> name
        _ * mock.identityPath >> project.identityPath.child(name)
        _ * mock.project >> project
        _ * mock.state >> Stub(TaskStateInternal) {
            getFailure() >> null
        }
        _ * mock.taskDependencies >> Stub(TaskDependency)
        _ * mock.finalizedBy >> Stub(TaskDependency)
        _ * mock.mustRunAfter >> Stub(TaskDependency)
        _ * mock.shouldRunAfter >> Stub(TaskDependency)
        _ * mock.compareTo(_) >> { Task t -> name.compareTo(t.name) }
        _ * mock.outputs >> Stub(TaskOutputsInternal) {
            getFiles() >> project.files()
        }
        _ * mock.path >> ":${name}"
        return mock
    }

    def brokenTask(String name, RuntimeException failure) {
        def mock = Mock(TaskInternal)
        _ * mock.name >> name
        _ * mock.identityPath >> project.identityPath.child(name)
        _ * mock.project >> project
        _ * mock.state >> Stub(TaskStateInternal) {
            getFailure() >> failure
        }
        _ * mock.taskDependencies >> Stub(TaskDependency)
        _ * mock.finalizedBy >> Stub(TaskDependency)
        _ * mock.mustRunAfter >> Stub(TaskDependency)
        _ * mock.shouldRunAfter >> Stub(TaskDependency)
        _ * mock.compareTo(_) >> { Task t -> name.compareTo(t.name) }
        _ * mock.outputs >> Stub(TaskOutputsInternal) {
            getFiles() >> project.files()
        }
        _ * mock.path >> ":${name}"
        return mock
    }
}
