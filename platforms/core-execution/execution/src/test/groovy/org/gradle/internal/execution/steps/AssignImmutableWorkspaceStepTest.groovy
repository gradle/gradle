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

import com.google.common.collect.ImmutableSortedMap
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.caching.internal.origin.OriginMetadataFactory
import org.gradle.caching.internal.origin.OriginReader
import org.gradle.caching.internal.origin.OriginWriter
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ImmutableWorkspace
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ImmutableWorkspace.TemporaryWorkspaceAction
import org.gradle.internal.file.FileType
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.vfs.FileSystemAccess

import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE

class AssignImmutableWorkspaceStepTest extends StepSpec<IdentityContext> {
    def immutableWorkspace = file("immutable-workspace")
    def temporaryWorkspace = file("temporary-workspace")
    def workspace = Stub(ImmutableWorkspace) {
        immutableLocation >> immutableWorkspace
        withTemporaryWorkspace(_ as TemporaryWorkspaceAction) >> { TemporaryWorkspaceAction action ->
            action.executeInTemporaryWorkspace(temporaryWorkspace)
        }
    }

    def fileSystemAccess = Mock(FileSystemAccess)
    def originMetadataFactory = Mock(OriginMetadataFactory)
    def outputSnapshotter = Mock(OutputSnapshotter)
    def workspaceProvider = Stub(ImmutableWorkspaceProvider) {
        getWorkspace(workId) >> workspace
    }

    def step = new AssignImmutableWorkspaceStep(fileSystemAccess, originMetadataFactory, outputSnapshotter, delegate)
    def work = Stub(ImmutableUnitOfWork)

    def setup() {
        work.workspaceProvider >> workspaceProvider
    }

    def "returns immutable workspace when already exists"() {
        immutableWorkspace.file("origin.bin").createFile()

        def delegateOriginMetadata = Mock(OriginMetadata)
        def existingWorkspaceSnapshot = Stub(DirectorySnapshot) {
            type >> FileType.Directory
        }
        def existingOutputs = ImmutableSortedMap.<String, FileSystemLocationSnapshot>of()
        def originReader = Stub(OriginReader) {
            execute(_ as InputStream) >> delegateOriginMetadata
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
        1 * outputSnapshotter.snapshotOutputs(work, immutableWorkspace) >> existingOutputs
        1 * originMetadataFactory.createReader() >> originReader
        0 * _
    }

    def "runs in temporary workspace when immutable workspace doesn't exist"() {
        def delegateExecution = Mock(ExecutionEngine.Execution)
        def delegateDuration = Duration.ofSeconds(1)
        def delegateResult = Stub(CachingResult) {
            execution >> Try.successful(delegateExecution)
            duration >> delegateDuration
        }
        def resolvedDelegateResult = Stub(Object)

        when:
        def result = step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> Stub(MissingFileSnapshot) {
            type >> FileType.Missing
        }

        then:
        1 * fileSystemAccess.write([temporaryWorkspace.absolutePath], _ as Runnable)

        then:
        1 * delegate.execute(work, _ as WorkspaceContext) >> { UnitOfWork work, WorkspaceContext delegateContext ->
            assert delegateContext.workspace == temporaryWorkspace
            temporaryWorkspace.file("output.txt").text = "output"
            return delegateResult
        }

        then:
        1 * originMetadataFactory.createWriter(identity.uniqueId, _, delegateDuration) >> Stub(OriginWriter)

        then:
        1 * fileSystemAccess.moveAtomically(temporaryWorkspace.absolutePath, immutableWorkspace.absolutePath) >> { String from, String to ->
            Files.move(Paths.get(from), Paths.get(to))
        }

        then:
        immutableWorkspace.file("output.txt").text == "output"
        immutableWorkspace.file("origin.bin").assertIsFile()
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
        1 * fileSystemAccess.write([temporaryWorkspace.absolutePath], _ as Runnable)

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
}
