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

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver
import org.gradle.api.internal.changedetection.changes.DefaultTaskExecutionMode
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecuterResult
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.properties.TaskProperties
import spock.lang.Specification
import spock.lang.Subject

@Subject(ResolveTaskExecutionModeExecuter)
class ResolveTaskExecutionModeExecuterTest extends Specification {
    final delegate = Mock(TaskExecuter)
    final taskProperties = Mock(TaskProperties)
    final task = Mock(TaskInternal)
    final taskState = Mock(TaskStateInternal)
    final taskContext = Mock(TaskExecutionContext)
    final repository = Mock(TaskExecutionModeResolver)
    final executionMode = DefaultTaskExecutionMode.incremental()

    final executer = new ResolveTaskExecutionModeExecuter(repository, delegate)

    def 'taskContext is initialized and cleaned as expected'() {
        when:
        executer.execute(task, taskState, taskContext)

        then: 'taskContext is initialized with task artifact state'
        1 * taskContext.taskProperties >> taskProperties
        1 * repository.getExecutionMode(task, taskProperties) >> executionMode
        1 * taskContext.setTaskExecutionMode(executionMode)

        then: 'delegate is executed'
        1 * delegate.execute(task, taskState, taskContext) >> TaskExecuterResult.WITHOUT_OUTPUTS

        then: 'task artifact state is removed from taskContext'
        1 * taskContext.setTaskExecutionMode(null)

        and: 'nothing else'
        0 * _
    }
}
