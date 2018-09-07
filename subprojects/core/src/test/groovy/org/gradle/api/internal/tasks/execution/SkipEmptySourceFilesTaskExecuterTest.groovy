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

import org.gradle.api.UncheckedIOException
import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.internal.OverlappingOutputs
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import spock.lang.Specification
import spock.lang.Subject

@Subject(SkipEmptySourceFilesTaskExecuter)
class SkipEmptySourceFilesTaskExecuterTest extends Specification {
    final target = Mock(TaskExecuter)
    final task = Mock(TaskInternal)
    final state = Mock(TaskStateInternal)
    final taskProperties = Mock(TaskProperties)
    final sourceFiles = Mock(FileCollectionInternal)
    final taskFiles = Mock(FileCollectionInternal)
    final taskInputsListener = Mock(TaskInputsListener)
    final taskContext = Mock(TaskExecutionContext)
    final taskArtifactState = Mock(TaskArtifactState)
    final taskExecutionHistory = Mock(TaskExecutionHistory)
    final cleanupRegistry = Mock(BuildOutputCleanupRegistry)
    final taskOutputChangesListener = Mock(TaskOutputChangesListener)
    final buildInvocationId = UniqueId.generate()
    final taskExecutionTime = 1L
    final originExecutionMetadata = new OriginTaskExecutionMetadata(buildInvocationId, taskExecutionTime)
    final executer = new SkipEmptySourceFilesTaskExecuter(taskInputsListener, cleanupRegistry, taskOutputChangesListener, target, new BuildInvocationScopeId(buildInvocationId))

    def 'skips task when sourceFiles are empty and previous output is empty'() {
        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.executionHistory >> taskExecutionHistory

        then: 'if no previous output files existed...'
        1 * taskExecutionHistory.outputFiles >> new HashSet<File>()

        then:
        1 * state.setOutcome(TaskExecutionOutcome.NO_SOURCE)

        then:
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'deletes previous output when sourceFiles are empty'() {
        given:
        def previousFile = Mock(File)
        Set<File> outputFiles = [previousFile]

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.executionHistory >> taskExecutionHistory
        1 * taskExecutionHistory.outputFiles >> outputFiles
        1 * taskExecutionHistory.overlappingOutputs >> null
        1 * taskOutputChangesListener.beforeTaskOutputChanged()

        then: 'deleting the file succeeds'
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> true
        _ * previousFile.exists() >> true
        _ * previousFile.isDirectory() >> false
        1 * previousFile.delete() >> true

        then:
        1 * state.setOutcome(TaskExecutionOutcome.EXECUTED)
        1 * taskArtifactState.snapshotAfterTaskExecution(null, buildInvocationId, taskContext)

        then:
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'does not delete previous output when they are not safe to delete'() {
        given:
        def previousFile = Mock(File)
        Set<File> outputFiles = [previousFile]

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.executionHistory >> taskExecutionHistory
        1 * taskExecutionHistory.outputFiles >> outputFiles
        1 * taskExecutionHistory.overlappingOutputs >> null
        1 * taskOutputChangesListener.beforeTaskOutputChanged()

        then: 'deleting the file succeeds'
        1 * previousFile.exists() >> true
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> false

        then:
        1 * state.setOutcome(TaskExecutionOutcome.NO_SOURCE)
        1 * taskArtifactState.snapshotAfterTaskExecution(null, originExecutionMetadata.buildInvocationId, taskContext)

        then:
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'does not delete directories when there are overlapping outputs'() {
        given:
        def previousFile = Mock(File)
        def previousDirectory = Mock(File)
        Set<File> outputFiles = [previousFile, previousDirectory]

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.executionHistory >> taskExecutionHistory
        1 * taskExecutionHistory.outputFiles >> outputFiles
        1 * taskExecutionHistory.overlappingOutputs >> new OverlappingOutputs("outputProperty", "some/path")
        1 * taskOutputChangesListener.beforeTaskOutputChanged()

        then: 'deleting the file succeeds'
        _ * previousFile.exists() >> true
        _ * previousDirectory.exists() >> true
        _ * previousDirectory.compareTo(previousFile) >> 1
        _ * previousFile.compareTo(previousDirectory) >> -1
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> true
        1 * cleanupRegistry.isOutputOwnedByBuild(previousDirectory) >> true
        _ * previousFile.isDirectory() >> false
        _ * previousDirectory.isDirectory() >> true
        1 * previousFile.delete() >> true

        then:
        1 * state.setOutcome(TaskExecutionOutcome.EXECUTED)
        1 * taskArtifactState.snapshotAfterTaskExecution(null, buildInvocationId, taskContext)

        then:
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'exception thrown when sourceFiles are empty and deletes previous output, but delete fails'() {
        given:
        def previousFile = Mock(File)
        Set<File> outputFiles = [previousFile]

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        1 * taskContext.taskArtifactState >> taskArtifactState
        1 * taskArtifactState.executionHistory >> taskExecutionHistory
        1 * taskExecutionHistory.outputFiles >> outputFiles
        1 * taskExecutionHistory.overlappingOutputs >> null
        1 * taskOutputChangesListener.beforeTaskOutputChanged()

        then: 'deleting the previous file fails'
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> true
        _ * previousFile.exists() >> true
        _ * previousFile.isDirectory() >> false
        1 * previousFile.delete() >> false
        1 * previousFile.toString() >> "output"

        then:
        def exception = thrown UncheckedIOException
        exception.message.contains("Unable to delete file: output")
    }

    def 'executes task when sourceFiles are not empty'() {
        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> false

        then:
        1 * taskProperties.getInputFiles() >> taskFiles
        1 * target.execute(task, state, taskContext)
        1 * taskInputsListener.onExecute(task, taskFiles)

        then:
        0 * _
    }

    def 'executes task when it has not declared any source files'() {
        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> false

        then:
        1 * taskProperties.getInputFiles() >> taskFiles
        1 * target.execute(task, state, taskContext)
        1 * taskInputsListener.onExecute(task, taskFiles)

        then:
        0 * _
    }
}
