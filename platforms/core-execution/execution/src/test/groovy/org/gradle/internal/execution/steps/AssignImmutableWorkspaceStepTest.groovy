/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableSortedMap
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.ExecutionOutputState
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadata
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ImmutableWorkspace
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ImmutableWorkspace.TemporaryWorkspaceAction
import org.gradle.internal.file.Deleter
import org.gradle.internal.file.FileType
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.TestSnapshotFixture
import org.gradle.internal.vfs.FileSystemAccess

import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE

class AssignImmutableWorkspaceStepTest extends StepSpec<IdentityContext> implements TestSnapshotFixture {
    def immutableWorkspace = file("immutable-workspace")
    def temporaryWorkspace = file("temporary-workspace")
    def workspace = Stub(ImmutableWorkspace) {
        immutableLocation >> immutableWorkspace
        withTemporaryWorkspace(_ as TemporaryWorkspaceAction) >> { TemporaryWorkspaceAction action ->
            action.executeInTemporaryWorkspace(temporaryWorkspace)
        }
    }

    def deleter = Mock(Deleter)
    def fileSystemAccess = Mock(FileSystemAccess)
    def immutableWorkspaceMetadataStore = Mock(ImmutableWorkspaceMetadataStore)
    def outputSnapshotter = Mock(OutputSnapshotter)
    def workspaceProvider = Stub(ImmutableWorkspaceProvider) {
        getWorkspace(workId) >> workspace
    }

    def step = new AssignImmutableWorkspaceStep(deleter, fileSystemAccess, immutableWorkspaceMetadataStore, outputSnapshotter, delegate)
    def work = Stub(ImmutableUnitOfWork)

    def setup() {
        work.workspaceProvider >> workspaceProvider
    }

    def "returns immutable workspace when already exists"() {
        def outputFile = immutableWorkspace.file("output.txt")
        def outputFileSnapshot = regularFile(outputFile.absolutePath)

        def delegateOriginMetadata = Mock(OriginMetadata)
        def existingWorkspaceSnapshot = Stub(DirectorySnapshot) {
            type >> FileType.Directory
        }

        def existingOutputs = ImmutableSortedMap.<String, FileSystemLocationSnapshot> of(
            "output", outputFileSnapshot
        )

        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == UP_TO_DATE
        result.afterExecutionOutputState.get().successful
        result.afterExecutionOutputState.get().originMetadata == delegateOriginMetadata
        result.afterExecutionOutputState.get().outputFilesProducedByWork == existingOutputs
        result.reusedOutputOriginMetadata.get() == delegateOriginMetadata

        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> existingWorkspaceSnapshot

        then:
        1 * outputSnapshotter.snapshotOutputs(work, immutableWorkspace) >> existingOutputs

        then:
        1 * immutableWorkspaceMetadataStore.loadWorkspaceMetadata(immutableWorkspace) >> Stub(ImmutableWorkspaceMetadata) {
            getOriginMetadata() >> delegateOriginMetadata
            getOutputPropertyHashes() >> ImmutableListMultimap.of("output", outputFileSnapshot.hash)
        }
        0 * _
    }

    def "runs in temporary workspace when immutable workspace doesn't exist"() {
        def delegateExecution = Mock(ExecutionEngine.Execution)
        def delegateDuration = Duration.ofSeconds(1)
        def delegateOriginMetadata = Stub(OriginMetadata)
        def delegateOutputFiles = ImmutableSortedMap.of()
        def delegateOutputState = Stub(ExecutionOutputState) {
            getOriginMetadata() >> delegateOriginMetadata
            getOutputFilesProducedByWork() >> delegateOutputFiles
        }
        def delegateResult = Stub(CachingResult) {
            getExecution() >> Try.successful(delegateExecution)
            getDuration() >> delegateDuration
            getAfterExecutionOutputState() >> Optional.of(delegateOutputState)
        }
        def resolvedDelegateResult = Stub(Object)

        when:
        def result = step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> Stub(MissingFileSnapshot) {
            type >> FileType.Missing
        }

        then:
        1 * fileSystemAccess.invalidate([temporaryWorkspace.absolutePath])

        then:
        1 * delegate.execute(work, _ as WorkspaceContext) >> { UnitOfWork work, WorkspaceContext delegateContext ->
            assert delegateContext.workspace == temporaryWorkspace
            temporaryWorkspace.file("output.txt").text = "output"
            return delegateResult
        }

        then:
        1 * immutableWorkspaceMetadataStore.storeWorkspaceMetadata(temporaryWorkspace, _) >> { File workspace, ImmutableWorkspaceMetadata metadata ->
            metadata.originMetadata == delegateOriginMetadata
        }

        then:
        1 * fileSystemAccess.moveAtomically(temporaryWorkspace.absolutePath, immutableWorkspace.absolutePath) >> { String from, String to ->
            Files.move(Paths.get(from), Paths.get(to))
        }

        then:
        immutableWorkspace.file("output.txt").text == "output"
        0 * _

        when:
        def resolvedResult = result.resolveOutputFromWorkspaceAs(Object)

        then:
        resolvedResult.get() == resolvedDelegateResult

        1 * delegateExecution.getOutput(immutableWorkspace) >> resolvedDelegateResult
        0 * _
    }

    def "keeps failed outputs in temporary workspace"() {
        def delegateFailure = Mock(Exception)
        def delegateDuration = Duration.ofSeconds(1)
        def delegateResult = Stub(CachingResult) {
            execution >> Try.failure(delegateFailure)
            duration >> delegateDuration
        }

        when:
        def result = step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> Stub(MissingFileSnapshot) {
            type >> FileType.Missing
        }

        then:
        1 * fileSystemAccess.invalidate([temporaryWorkspace.absolutePath])

        then:
        1 * delegate.execute(work, _ as WorkspaceContext) >> { UnitOfWork work, WorkspaceContext delegateContext ->
            assert delegateContext.workspace == temporaryWorkspace
            temporaryWorkspace.file("output.txt").text = "output"
            return delegateResult
        }

        then:
        immutableWorkspace.assertDoesNotExist()
        temporaryWorkspace.file("output.txt").assertIsFile()
        temporaryWorkspace.file("origin.bin").assertDoesNotExist()
        0 * _

        when:
        def resolvedResult = result.resolveOutputFromWorkspaceAs(Object)

        then:
        resolvedResult.failure.get() == delegateFailure
        0 * _
    }

    def "fails when immutable workspace has been tampered with"() {
        def outputFile = immutableWorkspace.file("output.txt")
        def originalOutputFileSnapshot = regularFile(outputFile.absolutePath, 1234L)
        def changedOutputFileSnapshot = regularFile(outputFile.absolutePath, 5678L)
        def delegateOriginMetadata = Stub(OriginMetadata)

        def existingWorkspaceSnapshot = Stub(DirectorySnapshot) {
            type >> FileType.Directory
        }

        def originalOutputs = ImmutableSortedMap.<String, FileSystemLocationSnapshot> of(
            "output", originalOutputFileSnapshot
        )
        def changedOutputs = ImmutableSortedMap.<String, FileSystemLocationSnapshot> of(
            "output", changedOutputFileSnapshot
        )

        when:
        step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> existingWorkspaceSnapshot

        then:
        1 * outputSnapshotter.snapshotOutputs(work, immutableWorkspace) >> changedOutputs
        1 * immutableWorkspaceMetadataStore.loadWorkspaceMetadata(immutableWorkspace) >> Stub(ImmutableWorkspaceMetadata) {
            getOriginMetadata() >> delegateOriginMetadata
            getOutputPropertyHashes() >> ImmutableListMultimap.of("output", originalOutputFileSnapshot.hash)
        }

        then:
        def ex = thrown IllegalStateException
        ex.message.startsWith("Workspace has been changed")
        0 * _
    }
}
