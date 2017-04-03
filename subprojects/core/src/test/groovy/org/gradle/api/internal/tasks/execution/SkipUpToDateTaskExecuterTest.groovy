/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification

class SkipUpToDateTaskExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def task = Mock(TaskInternal)
    def taskState = Mock(TaskStateInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskArtifactState = Mock(TaskArtifactState)
    Action<Task> action = Mock(Action)

    def executer = new SkipUpToDateTaskExecuter(delegate)

    def skipsTaskWhenOutputsAreUpToDate() {
        when:
        executer.execute(task, taskState, taskContext);

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.isUpToDate(_) >> true
        1 * taskState.setOutcome(TaskExecutionOutcome.UP_TO_DATE)
        1 * taskArtifactState.finished()
        0 * _
    }

    def executesTaskWhenOutputsAreNotUpToDate() {
        when:
        executer.execute(task, taskState, taskContext);

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.isUpToDate(_) >> false

        then:
        1 * taskArtifactState.beforeTask()

        then:
        1 * delegate.execute(task, taskState, taskContext)
        _ * taskState.getFailure() >> null

        then:
        1 * taskArtifactState.afterTask()
        1 * taskArtifactState.finished()
        0 * _
    }

    def doesNotUpdateStateWhenTaskFails() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.isUpToDate(_) >> false

        then:
        1 * taskArtifactState.beforeTask()

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * taskState.getFailure() >> new RuntimeException()

        then:
        1 * taskArtifactState.finished()
        0 * _
    }
}
