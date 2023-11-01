/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.UnitOfWork.OutputVisitor
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.OverlappingOutputs
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.workspace.Workspace
import org.gradle.internal.file.TreeType
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

class RemovePreviousOutputsStepTest extends StepSpec<ChangingOutputsContext> implements SnapshotterFixture {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def previousExecutionState = Mock(PreviousExecutionState)
    def beforeExecutionState = Mock(BeforeExecutionState)
    def delegateResult = Mock(Result)
    def outputChangeListener = Mock(OutputChangeListener)
    def deleter = TestFiles.deleter()

    def step = new RemovePreviousOutputsStep<>(deleter, outputChangeListener, delegate)


    def "deletes only the previous outputs"() {
        def outputs = new WorkOutputs()
        outputs.createContents()
        outputs.snapshot()
        outputs.dir.file("some/notOutput1.txt") << "notOutput1"
        outputs.dir.createDir("some/notOutput2")

        when:
        step.execute(work, context)
        then:
        interaction {
            cleanupOverlappingOutputs(outputs)
        }
        1 * delegate.execute(work, context) >> delegateResult
        0 * _

        !outputs.file.exists()
        outputs.dir.assertHasDescendants("some/notOutput1.txt", "some/notOutput2")
        !outputs.dir.file("some/dir").exists()
        !outputs.dir.file("some/lonelyDir").exists()
        !outputs.dir.file("some/another").exists()
        outputs.dir.file("some/notOutput2").isDirectory()
    }

    def "does not clean prepared directories"() {
        def outputs = new WorkOutputs()
        outputs.dir.createDir()
        outputs.file << "output"
        outputs.snapshot()

        when:
        step.execute(work, context)
        then:
        interaction {
            cleanupOverlappingOutputs(outputs)
        }
        1 * delegate.execute(work, context) >> delegateResult
        0 * _

        !outputs.file.exists()
        outputs.dir.isDirectory()
        outputs.file.parentFile.isDirectory()
    }

    def "works with missing root directories"() {
        def outputs = new WorkOutputs()
        outputs.dir.parentFile.mkdirs()
        outputs.file.parentFile.mkdirs()
        outputs.snapshot()

        when:
        step.execute(work, context)
        then:
        interaction {
            cleanupOverlappingOutputs(outputs)
        }
        1 * delegate.execute(work, context) >> delegateResult
        0 * _

        !outputs.file.exists()
        !outputs.dir.exists()
        outputs.file.parentFile.isDirectory()
    }

    def "cleans everything when there are no overlapping outputs"() {
        def outputs = new WorkOutputs()
        outputs.createContents()

        when:
        step.execute(work, context)
        then:
        interaction {
            cleanupExclusiveOutputs(outputs)
        }
        1 * delegate.execute(work, context) >> delegateResult
        0 * _

        !outputs.file.exists()
        outputs.dir.isDirectory()
        outputs.dir.list() as List == []
    }

    def "cleanup for exclusive output works with missing files"() {
        def outputs = new WorkOutputs()
        outputs.dir.parentFile.mkdirs()
        outputs.file.parentFile.mkdirs()

        when:
        step.execute(work, context)
        then:
        interaction {
            cleanupExclusiveOutputs(outputs)
        }
        1 * delegate.execute(work, context) >> delegateResult
        0 * _

        !outputs.file.exists()
        !outputs.dir.exists()
    }

    def "does not cleanup outputs when build is incremental"() {
        when:
        step.execute(work, context)
        then:
        _ * context.incrementalExecution >> true
        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    def "does not cleanup outputs when work does not opt-in"() {
        when:
        step.execute(work, context)
        then:
        _ * context.incrementalExecution >> false
        _ * work.shouldCleanupOutputsOnNonIncrementalExecution() >> false
        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    def "does cleanup outputs when work does not request input changes"() {
        def outputs = new WorkOutputs()
        outputs.createContents()

        when:
        step.execute(work, context)
        then:
        interaction {
            cleanupExclusiveOutputs(outputs, false)
        }
        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    void cleanupOverlappingOutputs(WorkOutputs outputs) {
        _ * context.incrementalExecution >> false
        _ * work.shouldCleanupOutputsOnNonIncrementalExecution() >> true
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        1 * beforeExecutionState.detectedOverlappingOutputs >> Optional.of(new OverlappingOutputs("test", "/absolute/path"))
        _ * work.visitOutputs(_, _) >> { Workspace.WorkspaceLocation workspace, OutputVisitor visitor ->
            visitor.visitOutputProperty("dir", TreeType.DIRECTORY, UnitOfWork.OutputFileValueSupplier.fromStatic(outputs.dir, TestFiles.fixed(outputs.dir)))
            visitor.visitOutputProperty("file", TreeType.FILE, UnitOfWork.OutputFileValueSupplier.fromStatic(outputs.file, TestFiles.fixed(outputs.file)))
        }
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        1 * previousExecutionState.outputFilesProducedByWork >> ImmutableSortedMap.of("dir", outputs.dirSnapshot, "file", outputs.fileSnapshot)
        1 * outputChangeListener.invalidateCachesFor({ Iterable<String> paths -> paths as List == [outputs.dir.absolutePath] })
        1 * outputChangeListener.invalidateCachesFor({ Iterable<String> paths -> paths as List == [outputs.file.absolutePath] })
    }

    void cleanupExclusiveOutputs(WorkOutputs outputs, boolean incrementalExecution = false) {
        _ * context.incrementalExecution >> incrementalExecution
        _ * work.shouldCleanupOutputsOnNonIncrementalExecution() >> true
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        1 * beforeExecutionState.detectedOverlappingOutputs >> Optional.empty()
        _ * work.visitOutputs(_, _) >> { Workspace.WorkspaceLocation workspace, OutputVisitor visitor ->
            visitor.visitOutputProperty("dir", TreeType.DIRECTORY, UnitOfWork.OutputFileValueSupplier.fromStatic(outputs.dir, TestFiles.fixed(outputs.dir)))
            visitor.visitOutputProperty("file", TreeType.FILE, UnitOfWork.OutputFileValueSupplier.fromStatic(outputs.file, TestFiles.fixed(outputs.file)))
        }
    }

    class WorkOutputs {
        def dir = temporaryFolder.file("build/outputs/dir")
        def file = temporaryFolder.file("build/output-files/file.txt")
        FileSystemSnapshot dirSnapshot
        FileSystemSnapshot fileSnapshot

        void snapshot() {
            dirSnapshot = snapshot(dir)
            fileSnapshot = snapshot(file)
        }

        void createContents() {
            file.text = "output file"
            dir.file("some/dir/output1.txt") << "output1"
            dir.file("some/dir/output2.txt") << "output2"
            dir.file("some/output3.txt") << "output3"
            dir.file("some/another/output4.txt") << "output4"
            dir.createDir("some/lonelyDir")
        }
    }
}
