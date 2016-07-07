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

import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification

class SkipEmptySourceFilesTaskExecuterTest extends Specification {
    final TaskExecuter target = Mock()
    final TaskInternal task = Mock()
    final TaskStateInternal state = Mock()
    final TaskExecutionContext executionContext = Mock()
    final taskInputs = Mock(TaskInputsInternal)
    final FileCollectionInternal sourceFiles = Mock()
    def taskInputsListener = Mock(TaskInputsListener)
    final TaskArtifactStateRepository taskArtifactStateRepository = Mock()
    final TaskArtifactState taskArtifactState = Mock()
    final TaskExecutionHistory taskExecutionHistory = Mock()
    final FileCollection outputFiles = new SimpleFileCollection()
    final SkipEmptySourceFilesTaskExecuter executer = new SkipEmptySourceFilesTaskExecuter(taskInputsListener, taskArtifactStateRepository, target)

    def setup() {
        _ * task.inputs >> taskInputs
        _ * taskInputs.sourceFiles >> sourceFiles
        _ * taskArtifactStateRepository.getStateFor(_) >> taskArtifactState
        _ * taskArtifactState.executionHistory >> taskExecutionHistory
        _ * taskExecutionHistory.outputFiles >> outputFiles
    }

    def skipsTaskWhenItsSourceFilesCollectionIsEmpty() {
        given:
        taskInputs.hasSourceFiles >> true
        sourceFiles.empty >> true

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * state.skipped('SKIPPED')
        0 * target._
        0 * state._
        1 * taskInputsListener.onExecute(task, sourceFiles)
    }

    def executesTaskWhenItsSourceFilesCollectionIsNotEmpty() {
        given:
        taskInputs.hasSourceFiles >> true
        sourceFiles.empty >> false

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * target.execute(task, state, executionContext)
        0 * target._
        0 * state._
        1 * taskInputsListener.onExecute(task, _)
    }

    def executesTaskWhenTaskHasNotDeclaredAnySourceFiles() {
        given:
        taskInputs.hasSourceFiles >> false

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * target.execute(task, state, executionContext)
        0 * target._
        0 * state._
        1 * taskInputsListener.onExecute(task, _)
    }
}
