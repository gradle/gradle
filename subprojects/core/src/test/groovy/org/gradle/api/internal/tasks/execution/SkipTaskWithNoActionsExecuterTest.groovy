/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification

class SkipTaskWithNoActionsExecuterTest extends Specification {
    def task = Mock(TaskInternal)
    def state = Mock(TaskStateInternal)
    def executionContext = Mock(TaskExecutionContext)
    def target = Mock(TaskExecuter)
    def taskState = Mock(TaskStateInternal)
    def executor = new SkipTaskWithNoActionsExecuter(target)

    def setup() {
        _ * task.state >> taskState
    }

    def skipsTaskWithNoActionsAndMarksUpToDateIfAllItsDependenciesWereSkipped() {
        given:
        task.hasTaskActions() >> false
        taskState.hasDependencyDoneWork() >> false

        when:
        executor.execute(task, state, executionContext)

        then:
        1 * state.setActionable(false)
        1 * state.setOutcome(TaskExecutionOutcome.UP_TO_DATE)
        0 * target._
        0 * state._
    }

    def skipsTaskWithNoActionsAndMarksOutOfDateDateIfAnyOfItsDependenciesWereNotSkipped() {
        given:
        task.hasTaskActions() >> false
        taskState.skipped >> false
        taskState.hasDependencyDoneWork() >> true

        when:
        executor.execute(task, state, executionContext)

        then:
        1 * state.setActionable(false)
        1 * state.setOutcome(TaskExecutionOutcome.EXECUTED)
        0 * target._
        0 * state._
    }

    def executesTaskWithActions() {
        given:
        task.hasTaskActions() >> true

        when:
        executor.execute(task, state, executionContext)

        then:
        1 * target.execute(task, state, executionContext)
        0 * target._
        0 * state._
    }
}
