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
import org.gradle.internal.TimeProvider
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.internal.progress.OperationResult
import org.gradle.internal.progress.OperationStartEvent
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DefaultTaskGraphExecuterSpec extends Specification {
    def cancellationToken = Mock(BuildCancellationToken)
    def project = ProjectBuilder.builder().build()
    def listenerManager = new DefaultListenerManager()
    def executer = Mock(TaskExecuter)
    def taskExecuter = new DefaultTaskGraphExecuter(listenerManager, new DefaultTaskPlanExecutor(), Factories.constant(executer), cancellationToken, Stub(TimeProvider), Stub(BuildOperationExecutor))

    def "notifies task listener as tasks are executed"() {
        def listener = Mock(TaskExecutionListener)
        def a = task("a")
        def b = task("b")

        given:
        taskExecuter.addTaskExecutionListener(listener)
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute()

        then:
        1 * listener.beforeExecute(a)
        1 * listener.afterExecute(a, a.state)

        then:
        1 * listener.beforeExecute(b)
        1 * listener.afterExecute(b, b.state)
        0 * listener._
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

    def "notifies internal task listener as tasks are executed"() {
        def listener = Mock(InternalTaskExecutionListener)
        def a = task("a")
        def failure = new RuntimeException()
        def b = brokenTask("b", failure)

        given:
        listenerManager.addListener(listener)
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute()

        then:
        RuntimeException e = thrown()
        e == failure
        1 * listener.beforeExecute(_, _) >> { TaskOperationInternal operation, OperationStartEvent startEvent ->
            assert operation.task == a
        }
        1 * listener.afterExecute(_, _) >> { TaskOperationInternal operation, OperationResult result ->
            assert operation.task == a
            assert result.failure == null
        }

        then:
        1 * listener.beforeExecute(_, _) >> { TaskOperationInternal operation, OperationStartEvent startEvent ->
            assert operation.task == b
        }
        1 * listener.afterExecute(_, _) >> { TaskOperationInternal operation, OperationResult result ->
            assert operation.task == b
            assert result.failure == failure
        }
        0 * listener._
    }

    def "wraps notification of internal listener around public listener"() {
        def listener1 = Mock(InternalTaskExecutionListener)
        def listener2 = Mock(TaskExecutionListener)
        def a = task("a")
        def b = task("b")

        given:
        listenerManager.addListener(listener1)
        listenerManager.addListener(listener2)
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute()

        then:
        1 * listener1.beforeExecute({it.task == a}, _)

        then:
        1 * listener2.beforeExecute(a)
        1 * listener2.afterExecute(a, a.state)

        then:
        1 * listener1.afterExecute({it.task == a}, _)

        then:
        1 * listener1.beforeExecute({it.task == b}, _)

        then:
        1 * listener2.beforeExecute(b)
        1 * listener2.afterExecute(b, b.state)

        then:
        1 * listener1.afterExecute({it.task == b}, _)
        0 * listener1._
        0 * listener2._
    }

    def "notifies internal listener of completion when public listener fails on task start"() {
        def listener1 = Mock(InternalTaskExecutionListener)
        def listener2 = Mock(TaskExecutionListener)
        def failure = new RuntimeException()
        def a = task("a")

        given:
        listenerManager.addListener(listener1)
        listenerManager.addListener(listener2)
        taskExecuter.addTasks([a])

        when:
        taskExecuter.execute()

        then:
        RuntimeException e = thrown()
        e == failure
        1 * listener1.beforeExecute({it.task == a}, _)

        then:
        1 * listener2.beforeExecute(a) >> { throw failure }

        then:
        1 * listener1.afterExecute({it.task == a}, _)
        0 * listener1._
        0 * listener2._
    }

    def "notifies internal listener of completion when public listener fails on task complete"() {
        def listener1 = Mock(InternalTaskExecutionListener)
        def listener2 = Mock(TaskExecutionListener)
        def failure = new RuntimeException()
        def a = task("a")

        given:
        listenerManager.addListener(listener1)
        listenerManager.addListener(listener2)
        taskExecuter.addTasks([a])

        when:
        taskExecuter.execute()

        then:
        RuntimeException e = thrown()
        e == failure
        1 * listener1.beforeExecute({it.task == a}, _)

        then:
        1 * listener2.beforeExecute(a)
        1 * listener2.afterExecute(a, a.state) >> { throw failure }

        then:
        1 * listener1.afterExecute({it.task == a}, _)
        0 * listener1._
        0 * listener2._
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

    def task(String name) {
        def mock = Mock(TaskInternal)
        _ * mock.name >> name
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
        return mock
    }

    def brokenTask(String name, RuntimeException failure) {
        def mock = Mock(TaskInternal)
        _ * mock.name >> name
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
        return mock
    }
}
