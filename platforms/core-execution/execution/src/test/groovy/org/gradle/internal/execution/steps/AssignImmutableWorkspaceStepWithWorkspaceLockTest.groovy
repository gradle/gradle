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
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadata
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.file.Deleter
import org.gradle.internal.file.FileType
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.TestSnapshotFixture
import org.gradle.internal.vfs.FileSystemAccess

import java.time.Duration
import java.util.function.Supplier

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE
import static org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.LockingImmutableWorkspace

class AssignImmutableWorkspaceStepWithWorkspaceLockTest extends StepSpec<IdentityContext> implements TestSnapshotFixture {
    def workspaceBaseDir = temporaryFolder.file("hash123")
    def immutableWorkspace = workspaceBaseDir.file("immutable-workspace")
    def workspace = Stub(LockingImmutableWorkspace) {
        immutableLocation >> immutableWorkspace
        withWorkspaceLock(_ as Supplier)
            >>
            { Supplier action ->
                workspaceBaseDir.mkdirs()
                workspaceBaseDir.file(workspaceBaseDir.name + ".lock").createFile()
                action.get()
            }
    }
    def deleter = Mock(Deleter)
    def fileSystemAccess = Mock(FileSystemAccess)
    def immutableWorkspaceMetadataStore = Mock(ImmutableWorkspaceMetadataStore)
    def outputSnapshotter = Mock(OutputSnapshotter)
    def workspaceProvider = Stub(ImmutableWorkspaceProvider) {
        getLockingWorkspace(workId) >> workspace
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
        def originalWorkspaceMetadata = Stub(ImmutableWorkspaceMetadata) {
            getOriginMetadata() >> delegateOriginMetadata
            getOutputPropertyHashes() >> ImmutableListMultimap.of("output", outputFileSnapshot.hash)
        }

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
        1 * immutableWorkspaceMetadataStore.loadWorkspaceMetadata(immutableWorkspace) >> Optional.of(originalWorkspaceMetadata)

        then:
        1 * outputSnapshotter.snapshotOutputs(work, immutableWorkspace) >> existingOutputs
        0 * _
    }

    def "fails when immutable workspace has been tampered with"() {
        def outputFile = immutableWorkspace.file("output.txt")
        outputFile.text = "output"

        def originalWorkspaceSnapshot = directory(immutableWorkspace.absolutePath, [
            regularFile(outputFile.absolutePath, 1234L)
        ])
        def originalWorkspaceMetadata = Stub(ImmutableWorkspaceMetadata) {
            getOriginMetadata() >> Stub(OriginMetadata)
            getOutputPropertyHashes() >> ImmutableListMultimap.of("output", originalWorkspaceSnapshot.hash)
        }
        def tamperedWorkspaceSnapshot = directory(immutableWorkspace.absolutePath, [
            regularFile(outputFile.absolutePath, 2345L)
        ])
        def tamperedOutputs = ImmutableSortedMap.<String, FileSystemLocationSnapshot> of(
            "output", tamperedWorkspaceSnapshot
        )

        when:
        step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> tamperedWorkspaceSnapshot
        1 * outputSnapshotter.snapshotOutputs(work, immutableWorkspace) >> tamperedOutputs
        1 * immutableWorkspaceMetadataStore.loadWorkspaceMetadata(immutableWorkspace) >> Optional.of(originalWorkspaceMetadata)

        then:
        def ex = thrown IllegalStateException
        ex.message.trim() == """
            The contents of the immutable workspace '${immutableWorkspace.absolutePath}' have been modified. These workspace directories are not supposed to be modified once they are created. The modification might have been caused by an external process, or could be the result of disk corruption.
            output:
             - immutable-workspace (Directory, 81e73263e0f667c2cbec4e2af9fcc55f)
               - output.txt (RegularFile, 29090000000000000000000000000000)
            """.stripIndent().trim()
    }

    def "execution runs and clears stale data if previous execution failed and there is no metadata"() {
        def outputFile = immutableWorkspace.file("output.txt").createFile()
        def outputFileSnapshot = regularFile(outputFile.absolutePath)
        def existingWorkspaceSnapshot = Stub(DirectorySnapshot) {
            type >> FileType.Directory
        }
        def delegateExecution = Stub(ExecutionEngine.Execution) {
            getOutcome() >> EXECUTED_NON_INCREMENTALLY
        }
        def delegateResult = Stub(CachingResult) {
            duration >> Duration.ZERO
            execution >> Try.successful(delegateExecution)
            afterExecutionOutputState >> Optional.of(new DefaultExecutionOutputState(true, ImmutableSortedMap.of(), Stub(OriginMetadata), false))
        }

        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        result.afterExecutionOutputState.get().successful

        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> existingWorkspaceSnapshot

        then:
        1 * deleter.deleteRecursively(immutableWorkspace)
        1 * immutableWorkspaceMetadataStore.loadWorkspaceMetadata(immutableWorkspace) >> Optional.empty()
        1 * fileSystemAccess.invalidate([immutableWorkspace.absolutePath.toString()])

        then:
        1 * delegate.execute(work, _ as WorkspaceContext) >> { UnitOfWork work, WorkspaceContext context ->
            assert context.workspace == workspace.immutableLocation
            return delegateResult
        }
        1 * immutableWorkspaceMetadataStore.storeWorkspaceMetadata(immutableWorkspace, _ as ImmutableWorkspaceMetadata)
        0 * _
    }
}
