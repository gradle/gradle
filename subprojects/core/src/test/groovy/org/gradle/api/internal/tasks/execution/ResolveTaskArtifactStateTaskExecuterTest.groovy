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

package org.gradle.api.internal.tasks.execution

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification
import spock.lang.Subject

@Subject(ResolveTaskArtifactStateTaskExecuter)
class ResolveTaskArtifactStateTaskExecuterTest extends Specification {
    final delegate = Mock(TaskExecuter)
    final outputs = Mock(TaskOutputsInternal)
    final task = Mock(TaskInternal)
    final taskState = Mock(TaskStateInternal)
    final taskContext = Mock(TaskExecutionContext)
    final repository = Mock(TaskArtifactStateRepository)
    final taskArtifactState = Mock(TaskArtifactState)
    final taskExecutionhistory = Mock(TaskExecutionHistory)
    final Action<Task> action = Mock(Action)

    final executer = new ResolveTaskArtifactStateTaskExecuter(repository, delegate)

    def 'taskContext is initialized and cleaned as expected'() {
        when:
        executer.execute(task, taskState, taskContext)

        then: 'taskContext is initialized with task artifact state'
        1 * repository.getStateFor(task) >> taskArtifactState
        1 * taskContext.setTaskArtifactState(taskArtifactState)
        1 * taskArtifactState.getExecutionHistory() >> taskExecutionhistory
        1 * task.getOutputs() >> outputs
        1 * outputs.setHistory(taskExecutionhistory)

        then: 'delegate is executed'
        1 * delegate.execute(task, taskState, taskContext)

        then: 'task artifact state is removed from taskContext'
        1 * outputs.setHistory(null)
        1 * taskContext.setTaskArtifactState(null)

        and: 'nothing else'
        0 * _
    }
}
