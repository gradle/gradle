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

import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification

class SkipTaskWithNoActionsExecuterTest extends Specification {
    final TaskInternal task = Mock()
    final TaskStateInternal state = Mock()
    final TaskExecutionContext executionContext = Mock()
    final TaskExecuter target = Mock()
    final TaskInternal dependency = Mock()
    final TaskStateInternal dependencyState = Mock()
    final TaskExecutionGraph taskExecutionGraph = Mock()
    final SkipTaskWithNoActionsExecuter executor = new SkipTaskWithNoActionsExecuter(taskExecutionGraph, target)

    def setup() {
        _ * taskExecutionGraph.getDependencies(task) >> ([dependency] as Set)
        _ * dependency.state >> dependencyState
    }

    def skipsTaskWithNoActionsAndMarksUpToDateIfAllItsDependenciesWereSkipped() {
        given:
        task.hasTaskActions() >> false
        dependencyState.skipped >> true

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
        dependencyState.skipped >> false

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
