/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess
import spock.lang.Specification

class DefaultTaskPlanExecutorTest extends Specification {
    def taskPlan = Mock(TaskExecutionPlan)
    def executionListener = Mock(TaskExecutionListener)
    def cacheAccess = Stub(TaskArtifactStateCacheAccess) {
        longRunningOperation(_, _) >> { name, action -> action.run() }
    }
    def executor = new DefaultTaskPlanExecutor(cacheAccess)

    def "executes tasks until no further tasks remain"() {
        def task = Mock(TaskInternal)
        def taskInfo = new TaskInfo(task)

        when:
        executor.process(taskPlan, executionListener)

        then:
        1 * taskPlan.taskToExecute >> taskInfo
        1 * executionListener.beforeExecute(task)
        1 * task.executeWithoutThrowingTaskFailure()
        1 * executionListener.afterExecute(task, _)
        1 * taskPlan.taskComplete(taskInfo)
        1 * taskPlan.taskToExecute >> null
        1 * taskPlan.awaitCompletion()
    }

    def "rethrows task execution failure"() {
        def failure = new RuntimeException()

        given:
        _ * taskPlan.awaitCompletion() >> { throw failure }

        when:
        executor.process(taskPlan, executionListener)

        then:
        def e = thrown(RuntimeException)
        e == failure
    }
}
