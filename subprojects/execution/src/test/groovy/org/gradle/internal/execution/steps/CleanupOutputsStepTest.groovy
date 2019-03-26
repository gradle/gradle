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
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.internal.execution.InputChangesContext
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.Step
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.UnitOfWork.OutputPropertyVisitor
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CleanupOutputsStepTest extends Specification implements FingerprinterFixture {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def delegate = Mock(Step)
    def context = Mock(InputChangesContext)
    def work = Mock(UnitOfWork)
    def afterPreviousExecution = Mock(AfterPreviousExecutionState)
    def delegateResult = Mock(Result)

    def step = new CleanupOutputsStep(delegate)

    def "deletes only the previous outputs"() {
        def outputs = new WorkOutputs()
        outputs.createContents()
        outputs.fingerprint()
        outputs.dir.file("some/notOutput1.txt") << "notOutput1"
        outputs.dir.createDir("some/notOutput2")

        when:
        step.execute(context)
        then:
        interaction {
            cleanupOverlappingOutputs(outputs)
        }
        1 * delegate.execute(_) >> delegateResult
        0 * _

        !outputs.file.exists()
        outputs.dir.assertHasDescendants("some/notOutput1.txt")
        !outputs.dir.file("some/dir").exists()
        !outputs.dir.file("some/lonelyDir").exists()
        !outputs.dir.file("some/another").exists()
        outputs.dir.file("some/notOutput2").isDirectory()
    }

    def "does not clean prepared directories"() {
        def outputs = new WorkOutputs()
        outputs.dir.createDir()
        outputs.file << "output"
        outputs.fingerprint()

        when:
        step.execute(context)
        then:
        interaction {
            cleanupOverlappingOutputs(outputs)
        }
        1 * delegate.execute(_) >> delegateResult
        0 * _

        !outputs.file.exists()
        outputs.dir.isDirectory()
        outputs.file.parentFile.isDirectory()
    }

    def "works with missing root directories"() {
        def outputs = new WorkOutputs()
        outputs.dir.parentFile.mkdirs()
        outputs.file.parentFile.mkdirs()
        outputs.fingerprint()

        when:
        step.execute(context)
        then:
        interaction {
            cleanupOverlappingOutputs(outputs)
        }
        1 * delegate.execute(_) >> delegateResult
        0 * _

        !outputs.file.exists()
        !outputs.dir.exists()
        outputs.file.parentFile.isDirectory()
    }

    def "cleans everything when there are no overlapping outputs"() {
        def outputs = new WorkOutputs()
        outputs.createContents()

        when:
        step.execute(context)
        then:
        interaction {
            cleanupExclusiveOutputs(outputs)
        }
        1 * delegate.execute(_) >> delegateResult
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
        step.execute(context)
        then:
        interaction {
            cleanupExclusiveOutputs(outputs)
        }
        1 * delegate.execute(_) >> delegateResult
        0 * _

        !outputs.file.exists()
        !outputs.dir.exists()
    }

    def "does not cleanup outputs when build is incremental"() {
        when:
        step.execute(context)
        then:
        1 * context.incrementalExecution >> true
        1 * delegate.execute(_) >> delegateResult
        0 * _
    }

    def "does not cleanup outputs when work does not opt-in"() {
        when:
        step.execute(context)
        then:
        1 * context.incrementalExecution >> false
        1 * context.work >> work
        1 * work.cleanupOutputsOnNonIncrementalExecution >> false
        1 * delegate.execute(_) >> delegateResult
        0 * _
    }

    void cleanupOverlappingOutputs(WorkOutputs outputs) {
        1 * context.incrementalExecution >> false
        1 * context.work >> work
        1 * work.cleanupOutputsOnNonIncrementalExecution >> true
        1 * work.overlappingOutputsDetected >> true
        1 * work.visitOutputProperties(_) >> { OutputPropertyVisitor visitor ->
            visitor.visitOutputProperty("dir", TreeType.DIRECTORY, ImmutableFileCollection.of(outputs.dir))
            visitor.visitOutputProperty("file", TreeType.FILE, ImmutableFileCollection.of(outputs.file))
        }
        1 * context.getAfterPreviousExecutionState() >> Optional.of(afterPreviousExecution)
        1 * afterPreviousExecution.outputFileProperties >> ImmutableSortedMap.<String, FileCollectionFingerprint>of("dir", outputs.dirFingerprint, "file", outputs.fileFingerprint)
    }

    void cleanupExclusiveOutputs(WorkOutputs outputs) {
        1 * context.incrementalExecution >> false
        1 * context.work >> work
        1 * work.cleanupOutputsOnNonIncrementalExecution >> true
        1 * work.overlappingOutputsDetected >> false
        1 * work.visitOutputProperties(_) >> { OutputPropertyVisitor visitor ->
            visitor.visitOutputProperty("dir", TreeType.DIRECTORY, ImmutableFileCollection.of(outputs.dir))
            visitor.visitOutputProperty("file", TreeType.FILE, ImmutableFileCollection.of(outputs.file))
        }
    }

    class WorkOutputs {
        def dir = temporaryFolder.file("build/outputs/dir")
        def file = temporaryFolder.file("build/output-files/file.txt")
        FileCollectionFingerprint dirFingerprint
        FileCollectionFingerprint fileFingerprint

        void fingerprint() {
            dirFingerprint = outputFingerprinter.fingerprint(ImmutableFileCollection.of(dir))
            fileFingerprint = outputFingerprinter.fingerprint(ImmutableFileCollection.of(file))
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
