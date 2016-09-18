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
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification
import spock.lang.Subject

@Subject(SkipEmptySourceFilesTaskExecuter)
class SkipEmptySourceFilesTaskExecuterTest extends Specification {
    final target = Mock(TaskExecuter)
    final task = Mock(TaskInternal)
    final state = Mock(TaskStateInternal)
    final taskInputs = Mock(TaskInputsInternal)
    final sourceFiles = Mock(FileCollectionInternal)
    final taskFiles = Mock(FileCollectionInternal)
    final taskInputsListener = Mock(TaskInputsListener)
    final taskContext = Mock(TaskExecutionContext)
    final taskArtifactState = Mock(TaskArtifactState)
    final taskExecutionHistory = Mock(TaskExecutionHistory)
    final outputFiles = Mock(FileCollection)
    final SkipEmptySourceFilesTaskExecuter executer = new SkipEmptySourceFilesTaskExecuter(taskInputsListener, target)

    def 'skips task when sourceFiles are empty and no previous output existed'() {
        when:
        executer.execute(task, state, taskContext)

        then:
        1 * task.inputs >> taskInputs
        1 * taskInputs.sourceFiles >> sourceFiles
        1 * taskInputs.hasSourceFiles >> true
        1 * sourceFiles.empty >> true

        then:
        1 * state.skipped('SKIPPED')

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.executionHistory >> taskExecutionHistory
        1 * taskExecutionHistory.outputFiles >> outputFiles

        then: 'if no previous output files existed...'
        1 * outputFiles.empty >> true

        then:
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'skips task when sourceFiles are empty and deletes previous output'() {
        given:
        def previousFile = Mock(File)
        Set<File> previousFiles = [previousFile]

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * task.inputs >> taskInputs
        1 * taskInputs.sourceFiles >> sourceFiles
        1 * taskInputs.hasSourceFiles >> true
        1 * sourceFiles.empty >> true

        then:
        1 * state.skipped('SKIPPED')

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.executionHistory >> taskExecutionHistory
        1 * taskExecutionHistory.outputFiles >> outputFiles

        then: 'if previous output files existed...'
        1 * outputFiles.empty >> false
        1 * outputFiles.files >> previousFiles

        then: 'deleting the file succeeds'
        1 * previousFile.isFile() >> true
        1 * previousFile.delete() >> true
        _ * previousFile.absolutePath // depends on log level

        then:
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'skips task when sourceFiles are empty and deletes previous output - but delete fails'() {
        given:
        def previousFile = Mock(File)
        Set<File> previousFiles = [previousFile]

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * task.inputs >> taskInputs
        1 * taskInputs.sourceFiles >> sourceFiles
        1 * taskInputs.hasSourceFiles >> true
        1 * sourceFiles.empty >> true

        then:
        1 * state.skipped('SKIPPED')

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.executionHistory >> taskExecutionHistory
        1 * taskExecutionHistory.outputFiles >> outputFiles

        then: 'if previous output files existed...'
        1 * outputFiles.empty >> false
        1 * outputFiles.files >> previousFiles

        then: 'deleting the previous file fails'
        1 * previousFile.isFile() >> true
        1 * previousFile.delete() >> false
        _ * previousFile.absolutePath // depends on log level

        then:
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'executes task when sourceFiles are not empty'() {
        when:
        executer.execute(task, state, taskContext)

        then:
        1 * task.inputs >> taskInputs
        1 * taskInputs.sourceFiles >> sourceFiles
        1 * taskInputs.hasSourceFiles >> true
        1 * sourceFiles.empty >> false

        then:
        1 * taskInputs.files >> taskFiles
        1 * target.execute(task, state, taskContext)
        1 * taskInputsListener.onExecute(task, taskFiles)

        then:
        0 * _
    }

    def 'executes task when it has not declared any source files'() {
        when:
        executer.execute(task, state, taskContext)

        then:
        1 * task.inputs >> taskInputs
        1 * taskInputs.hasSourceFiles >> false

        then:
        1 * taskInputs.files >> taskFiles
        1 * target.execute(task, state, taskContext)
        1 * taskInputsListener.onExecute(task, taskFiles)

        then:
        0 * _
    }
}
