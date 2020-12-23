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

package org.gradle.api.internal.tasks.execution

import org.gradle.api.DefaultTask
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.taskfactory.TaskIdentity
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecuterResult
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.execution.taskgraph.TaskListenerInternal
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.Path
import spock.lang.Specification

class EventFiringTaskExecuterTest extends Specification {

    def buildOperationExecutor = new TestBuildOperationExecutor()
    def taskExecutionListener = Mock(TaskExecutionListener)
    def taskListener = Mock(TaskListenerInternal)
    def delegate = Mock(TaskExecuter)
    def task = Mock(TaskInternal)
    def taskIdentity = new TaskIdentity(DefaultTask, "foo", null, null, null, 0)
    def state = new TaskStateInternal()
    def executionContext = Mock(TaskExecutionContext)

    def executer = new EventFiringTaskExecuter(buildOperationExecutor, taskExecutionListener, taskListener, delegate)

    def "notifies task listeners"() {
        when:
        executer.execute(task, state, executionContext)

        then:
        _ * task.getIdentityPath() >> Path.path(":a")

        1 * task.getTaskIdentity() >> taskIdentity
        1 * taskListener.beforeExecute(taskIdentity)
        1 * taskExecutionListener.beforeExecute(task)

        then:
        1 * delegate.execute(task, state, executionContext) >> TaskExecuterResult.WITHOUT_OUTPUTS

        then:
        1 * taskExecutionListener.afterExecute(task, state)
        1 * task.getTaskIdentity() >> taskIdentity
        1 * taskListener.afterExecute(taskIdentity, state)
        0 * taskExecutionListener._
        0 * taskListener._

        and:
        buildOperationExecutor.operations[0].name == ":a"
        buildOperationExecutor.operations[0].displayName == "Task :a"
        buildOperationExecutor.operations[0].progressDisplayName == ":a"
        buildOperationExecutor.operations[0].metadata == BuildOperationCategory.TASK
    }

    def "does not run task action when beforeExecute event fails"() {
        def failure = new RuntimeException()

        when:
        executer.execute(task, state, executionContext)

        then:
        _ * task.getIdentityPath() >> Path.path(":a")
        1 * task.getTaskIdentity() >> taskIdentity
        1 * taskListener.beforeExecute(taskIdentity)
        1 * taskExecutionListener.beforeExecute(task) >> { throw failure }
        0 * delegate._
        0 * taskExecutionListener._
        0 * taskListener._

        and:
        state.failure instanceof TaskExecutionException
        state.failure.cause == failure

        and:
        def operation = buildOperationExecutor.log.records[0]
        operation.failure != null
    }

    def "notifies task listeners when task execution fails"() {
        def failure = new RuntimeException()

        when:
        executer.execute(task, state, executionContext)

        then:
        _ * task.getIdentityPath() >> Path.path(":a")
        1 * task.getTaskIdentity() >> taskIdentity
        1 * taskListener.beforeExecute(taskIdentity)
        1 * taskExecutionListener.beforeExecute(task)

        then:
        1 * delegate.execute(task, state, executionContext) >> {
            state.setOutcome(failure)
            return TaskExecuterResult.WITHOUT_OUTPUTS
        }

        then:
        1 * taskExecutionListener.afterExecute(task, state)
        1 * task.getTaskIdentity() >> taskIdentity
        1 * taskListener.afterExecute(taskIdentity, state)
        0 * taskExecutionListener._
        0 * taskListener._

        and:
        state.failure == failure

        and:
        def operation = buildOperationExecutor.log.records[0]
        operation.failure != null
    }

    def "result of build operation is set even if listener throws exception"() {
        def failure = new RuntimeException()

        when:
        executer.execute(task, state, executionContext)

        then:
        _ * task.getIdentityPath() >> Path.path(":a")
        1 * taskExecutionListener.beforeExecute(task)

        then:
        1 * delegate.execute(task, state, executionContext) >> TaskExecuterResult.WITHOUT_OUTPUTS

        then:
        1 * taskExecutionListener.afterExecute(task, state) >> {
            throw failure
        }
        0 * taskExecutionListener._

        and:
        state.failure instanceof TaskExecutionException
        state.failure.cause == failure

        and:
        def operation = buildOperationExecutor.log.records[0]
        operation.failure != null
    }

    def "result of build operation is set even if both execution and listener fail"() {
        def failure = new RuntimeException("one")
        def failure2 = new RuntimeException("two")

        when:
        executer.execute(task, state, executionContext)

        then:
        _ * task.getIdentityPath() >> Path.path(":a")
        1 * taskExecutionListener.beforeExecute(task)

        then:
        1 * delegate.execute(task, state, executionContext) >> {
            state.setOutcome(failure)
            return TaskExecuterResult.WITHOUT_OUTPUTS
        }

        then:
        1 * taskExecutionListener.afterExecute(task, state) >> {
            throw failure2
        }
        0 * taskExecutionListener._

        and:
        state.failure instanceof TaskExecutionException
        state.failure.causes == [failure, failure2]

        and:
        def operation = buildOperationExecutor.log.records[0]
        operation.failure != null
    }
}
