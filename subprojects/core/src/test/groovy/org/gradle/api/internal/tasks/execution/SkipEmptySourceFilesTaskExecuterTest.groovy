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

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.UncheckedIOException
import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.internal.OverlappingOutputs
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultWellKnownFileLocations
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecuterResult
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.properties.TaskProperties
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@Subject(SkipEmptySourceFilesTaskExecuter)
class SkipEmptySourceFilesTaskExecuterTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder

    final target = Mock(TaskExecuter)
    final task = Mock(TaskInternal)
    final state = Mock(TaskStateInternal)
    final taskProperties = Mock(TaskProperties)
    final sourceFiles = Mock(FileCollectionInternal)
    final taskFiles = Mock(FileCollectionInternal)
    final taskInputsListener = Mock(TaskInputsListener)
    final taskContext = Mock(TaskExecutionContext)
    final cleanupRegistry = Mock(BuildOutputCleanupRegistry)
    final outputChangeListener = Mock(OutputChangeListener)
    final executionHistoryStore = Mock(ExecutionHistoryStore)
    final executer = new SkipEmptySourceFilesTaskExecuter(taskInputsListener, executionHistoryStore, cleanupRegistry, outputChangeListener, target)
    final stringInterner = new StringInterner()
    final fileSystemSnapshotter = new DefaultFileSystemSnapshotter(TestFiles.fileHasher(), stringInterner, TestFiles.fileSystem(), new DefaultFileSystemMirror(new DefaultWellKnownFileLocations([])))
    final fingerprinter = new AbsolutePathFileCollectionFingerprinter(fileSystemSnapshotter)

    def 'skips task when sourceFiles are empty and previous output is empty'() {
        def afterPreviousExecution = Mock(AfterPreviousExecutionState)

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        1 * taskContext.afterPreviousExecution >> afterPreviousExecution

        then: 'if no previous output files existed...'
        1 * afterPreviousExecution.outputFileProperties >> ImmutableSortedMap.of()

        then:
        1 * state.setOutcome(TaskExecutionOutcome.NO_SOURCE)
        1 * task.path >> "task"
        1 * executionHistoryStore.remove("task")
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'deletes previous output when sourceFiles are empty'() {
        given:
        def afterPreviousExecution = Mock(AfterPreviousExecutionState)
        def previousFile = temporaryFolder.file("output.txt")
        previousFile << "some content"
        def previousOutputFiles = ImmutableSortedMap.of(
                "output", fingerprinter.fingerprint(ImmutableFileCollection.of(previousFile))
        )
        def outputFilesBefore = ImmutableSortedMap.of(
                "output", fingerprinter.fingerprint(ImmutableFileCollection.of(previousFile))
        )

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        _ * taskContext.afterPreviousExecution >> afterPreviousExecution
        _ * afterPreviousExecution.outputFileProperties >> previousOutputFiles
        _ * taskContext.outputFilesBeforeExecution >> outputFilesBefore
        1 * taskContext.overlappingOutputs >> Optional.empty()
        1 * outputChangeListener.beforeOutputChange()

        then: 'deleting the file succeeds'
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> true

        then:
        1 * state.setOutcome(TaskExecutionOutcome.EXECUTED)
        1 * task.path >> "task"
        1 * executionHistoryStore.remove("task")
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        !previousFile.exists()
        0 * _
    }

    def 'does not delete previous output when they are not safe to delete'() {
        given:
        def afterPreviousExecution = Mock(AfterPreviousExecutionState)
        def previousFile = temporaryFolder.file("output.txt")
        previousFile.createNewFile()
        def previousOutputFiles = ImmutableSortedMap.of(
            "output", fingerprinter.fingerprint(ImmutableFileCollection.of(previousFile))
        )
        def outputFilesBefore = ImmutableSortedMap.of(
            "output", fingerprinter.fingerprint(ImmutableFileCollection.of(previousFile))
        )

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        _ * taskContext.afterPreviousExecution >> afterPreviousExecution
        _ * afterPreviousExecution.outputFileProperties >> previousOutputFiles
        _ * taskContext.outputFilesBeforeExecution >> outputFilesBefore
        1 * taskContext.overlappingOutputs >> Optional.empty()
        1 * outputChangeListener.beforeOutputChange()

        then: 'deleting the file succeeds'
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> false

        then:
        1 * state.setOutcome(TaskExecutionOutcome.NO_SOURCE)
        1 * task.path >> "task"
        1 * executionHistoryStore.remove("task")
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        previousFile.exists()
        0 * _
    }

    def 'does not delete directories when there are overlapping outputs'() {
        given:
        def outputFiles = []
        def outputDir = temporaryFolder.createDir("rootDir")

        outputFiles << outputDir.file("some-output.txt")
        def subDir = outputDir.createDir("subDir")
        outputFiles << subDir.file("in-subdir.txt")

        def outputFile = temporaryFolder.createFile("output.txt")
        outputFiles << outputFile

        outputFiles.each {
            it << "output ${it.name}"
        }

        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)
        def previousOutputFiles = ImmutableSortedMap.of(
            "output", fingerprinter.fingerprint(ImmutableFileCollection.of(outputDir, outputFile))
        )

        def overlappingFile = outputDir.file("overlap")
        overlappingFile << "overlapping file"
        def outputFilesBefore = ImmutableSortedMap.of(
            "output", fingerprinter.fingerprint(ImmutableFileCollection.of(outputDir, outputFile, overlappingFile))
        )
        def overlappingOutputs = new OverlappingOutputs("someProperty", "path/to/outputFile")

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        _ * taskContext.afterPreviousExecution >> afterPreviousExecutionState
        _ * afterPreviousExecutionState.outputFileProperties >> previousOutputFiles
        _ * taskContext.outputFilesBeforeExecution >> outputFilesBefore
        1 * taskContext.overlappingOutputs >> Optional.of(overlappingOutputs)
        1 * outputChangeListener.beforeOutputChange()

        then: 'deleting the file succeeds'
        5 * cleanupRegistry.isOutputOwnedByBuild(_) >> true
        outputDir.exists()
        subDir.exists()
        overlappingFile.exists()
        outputFiles.each {
            assert !it.exists()
        }

        then:
        1 * state.setOutcome(TaskExecutionOutcome.EXECUTED)
        1 * task.path >> "task"
        1 * executionHistoryStore.remove("task")
        1 * taskInputsListener.onExecute(task, sourceFiles)

        then:
        0 * _
    }

    def 'exception thrown when sourceFiles are empty and deletes previous output, but delete fails'() {
        given:
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)
        def previousFile = temporaryFolder.file("output.txt")
        previousFile.createNewFile()
        def previousOutputFiles = ImmutableSortedMap.of(
            "output", fingerprinter.fingerprint(ImmutableFileCollection.of(previousFile))
        )
        def outputFilesBefore = ImmutableSortedMap.of(
            "output", fingerprinter.fingerprint(ImmutableFileCollection.of(previousFile))
        )

        when:
        executer.execute(task, state, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskProperties.sourceFiles >> sourceFiles
        1 * taskProperties.hasSourceFiles() >> true
        1 * sourceFiles.empty >> true

        then:
        _ * taskContext.afterPreviousExecution >> afterPreviousExecutionState
        _ * afterPreviousExecutionState.outputFileProperties >> previousOutputFiles
        _ * taskContext.outputFilesBeforeExecution >> outputFilesBefore
        1 * taskContext.overlappingOutputs >> Optional.empty()
        1 * outputChangeListener.beforeOutputChange()

        then: 'deleting the previous file fails'
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> {
            // Delete the file here so that deletion in SkipEmptySourceFilesTaskExecuter fails
            assert previousFile.delete()
            return true
        }

        then:
        UncheckedIOException exception = thrown()
        exception.message.contains("java.io.FileNotFoundException: File does not exist")
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
        1 * target.execute(task, state, taskContext) >> TaskExecuterResult.WITHOUT_OUTPUTS
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
        1 * target.execute(task, state, taskContext) >> TaskExecuterResult.WITHOUT_OUTPUTS
        1 * taskInputsListener.onExecute(task, taskFiles)

        then:
        0 * _
    }
}
