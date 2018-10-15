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

import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.Path
import spock.lang.Specification

class EventFiringTaskExecuterTest extends Specification {

    def buildOperationExecutor = new TestBuildOperationExecutor()
    def taskExecutionListener = Mock(TaskExecutionListener)
    def delegate = Mock(TaskExecuter)
    def task = Mock(TaskInternal)
    def state = Mock(TaskStateInternal)
    def executionContext = Mock(TaskExecutionContext)

    def executer = new EventFiringTaskExecuter(buildOperationExecutor, taskExecutionListener, delegate)

    def "notifies task listeners"() {
        when:
        executer.execute(task, state, executionContext)

        then:
        1 * taskExecutionListener.beforeExecute(task)
        _ * task.getIdentityPath() >> Path.path(":a")

        then:
        1 * delegate.execute(task, state, executionContext)

        then:
        1 * taskExecutionListener.afterExecute(task, state)
        0 * taskExecutionListener._

        and:
        buildOperationExecutor.operations[0].name == ":a"
        buildOperationExecutor.operations[0].displayName == "Task :a"
        buildOperationExecutor.operations[0].progressDisplayName == ":a"
        buildOperationExecutor.operations[0].operationType == BuildOperationCategory.TASK
    }

    def "result of buildoperation is set even if listener throws exception"() {
        def failure = new RuntimeException()

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * taskExecutionListener.beforeExecute(task)
        _ * task.getIdentityPath() >> Path.path(":a")

        then:
        1 * delegate.execute(task, state, executionContext)

        then:
        1 * taskExecutionListener.afterExecute(task, state) >> {
            throw failure
        }

        then:
        def e = thrown(RuntimeException)
        e.is(failure)
        buildOperationExecutor.log.mostRecentResult(ExecuteTaskBuildOperationType)
    }
}
