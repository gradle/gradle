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

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.invocation.Gradle
import spock.lang.Specification

class DefaultTaskPlanExecutorTest extends Specification {
    def taskPlan = Mock(TaskExecutionPlan)
    def worker = Mock(Action)
    def executor = new DefaultTaskPlanExecutor()

    def "executes tasks until no further tasks remain"() {
        def gradle = Mock(Gradle)
        def project = Mock(Project)
        def task = Mock(TaskInternal)
        def state = Mock(TaskStateInternal)
        project.gradle >> gradle
        task.project >> project
        task.state >> state
        def taskInfo = new TaskInfo(task)

        when:
        executor.process(taskPlan, worker)

        then:
        1 * taskPlan.taskToExecute >> taskInfo
        1 * worker.execute(task)
        1 * taskPlan.taskComplete(taskInfo)
        1 * taskPlan.taskToExecute >> null
        1 * taskPlan.awaitCompletion()
    }

    def "rethrows task execution failure"() {
        def failure = new RuntimeException()

        given:
        _ * taskPlan.awaitCompletion() >> { throw failure }

        when:
        executor.process(taskPlan, worker)

        then:
        def e = thrown(RuntimeException)
        e == failure
    }
}
