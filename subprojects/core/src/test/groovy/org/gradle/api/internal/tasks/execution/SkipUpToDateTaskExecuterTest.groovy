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
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification

public class SkipUpToDateTaskExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def outputs = Mock(TaskOutputsInternal)
    def task = Mock(TaskInternal)
    def taskState = Mock(TaskStateInternal)
    def repository = Mock(TaskArtifactStateRepository)
    def taskArtifactState = Mock(TaskArtifactState)
    def executionHistory = Mock(TaskExecutionHistory)
    def executer = new SkipUpToDateTaskExecuter(delegate, repository)

    def skipsTaskWhenOutputsAreUpToDate() {
        when:
        executer.execute(task, taskState);

        then:
        1 * repository.getStateFor(task) >> taskArtifactState
        1 * taskArtifactState.isUpToDate() >> true
        1 * taskState.upToDate()
        1 * taskArtifactState.finished()
        0 * _
    }
    
    def executesTaskWhenOutputsAreNotUpToDate() {
        when:
        executer.execute(task, taskState);

        then:
        1 * repository.getStateFor(task) >> taskArtifactState
        1 * taskArtifactState.isUpToDate() >> false

        then:
        1 * taskArtifactState.beforeTask()
        1 * taskArtifactState.getExecutionHistory() >> executionHistory
        2 * task.outputs >> outputs
        1 * outputs.setHistory(executionHistory)
        1 * task.isIncrementalTask() >> true
        1 * outputs.setTaskArtifactState(taskArtifactState)

        then:
        1 * delegate.execute(task, taskState)
        _ * taskState.getFailure() >> null

        then:
        1 * taskArtifactState.afterTask()
        1 * task.outputs >> outputs
        1 * outputs.setHistory(null)
        1 * taskArtifactState.finished()
        0 * _
    }

    def doesNotUpdateStateWhenTaskFails() {
        when:
        executer.execute(task, taskState)

        then:
        1 * repository.getStateFor(task) >> taskArtifactState
        1 * taskArtifactState.isUpToDate() >> false

        then:
        1 * taskArtifactState.beforeTask()
        1 * taskArtifactState.getExecutionHistory() >> executionHistory
        1 * task.outputs >> outputs
        1 * outputs.setHistory(executionHistory)
        1 * task.isIncrementalTask() >> false

        then:
        1 * delegate.execute(task, taskState)
        1 * taskState.getFailure() >> new RuntimeException()

        then:
        1 * task.outputs >> outputs
        1 * outputs.setHistory(null)
        1 * taskArtifactState.finished()
        0 * _
    }
}
