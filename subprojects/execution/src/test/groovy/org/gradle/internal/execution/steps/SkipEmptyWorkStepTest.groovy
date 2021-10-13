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
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.fingerprint.InputFingerprinter
import org.gradle.internal.execution.fingerprint.impl.DefaultInputFingerprinter
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.file.Deleter
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.ValueSnapshot

import static org.gradle.internal.execution.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
import static org.gradle.internal.execution.ExecutionOutcome.SHORT_CIRCUITED

class SkipEmptyWorkStepTest extends StepSpec<PreviousExecutionContext> {
    def buildOutputCleanupRegistry = Mock(BuildOutputCleanupRegistry)
    def deleter = Mock(Deleter)
    def outputChangeListener = Mock(OutputChangeListener)
    def inputFingerprinter = Mock(InputFingerprinter)
    def fileCollectionSnapshotter = TestFiles.fileCollectionSnapshotter()

    def step = new SkipEmptyWorkStep(
        buildOutputCleanupRegistry,
        deleter,
        outputChangeListener,
        delegate)

    def knownSnapshot = Mock(ValueSnapshot)
    def knownFileFingerprint = Mock(CurrentFileCollectionFingerprint)
    def sourceFileFingerprint = Mock(CurrentFileCollectionFingerprint)
    Optional skipOutcome

    @Override
    protected PreviousExecutionContext createContext() {
        Stub(PreviousExecutionContext) {
            getInputProperties() >> ImmutableSortedMap.of()
            getInputFileProperties() >> ImmutableSortedMap.of()
        }
    }

    def setup() {
        _ * work.broadcastRelevantFileSystemInputs(_) >> { ExecutionOutcome outcome -> assert skipOutcome == null; skipOutcome = Optional.ofNullable(outcome) }
        _ * work.inputFingerprinter >> inputFingerprinter
    }

    def "delegates when work has no source properties"() {
        when:
        step.execute(work, context)

        then:
        _ * context.inputProperties >> ImmutableSortedMap.of("known", knownSnapshot)
        _ * context.inputFileProperties >> ImmutableSortedMap.of("known-file", knownFileFingerprint)
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("known", knownSnapshot),
            ImmutableSortedMap.of("known-file", knownFileFingerprint),
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of())

        1 * delegate.execute(work, _ as PreviousExecutionContext) >> { UnitOfWork work, PreviousExecutionContext delegateContext ->
            assert delegateContext.inputProperties as Map == ["known": knownSnapshot]
            assert delegateContext.inputFileProperties as Map == ["known-file": knownFileFingerprint]
        }
        0 * _

        then:
        !skipOutcome.present
    }

    def "delegates when work has sources"() {
        when:
        step.execute(work, context)

        then:
        _ * context.inputProperties >> ImmutableSortedMap.of("known", knownSnapshot)
        _ * context.inputFileProperties >> ImmutableSortedMap.of("known-file", knownFileFingerprint)
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("known", knownSnapshot),
            ImmutableSortedMap.of("known-file", knownFileFingerprint),
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("source-file", sourceFileFingerprint))

        then:
        1 * sourceFileFingerprint.empty >> false

        then:
        1 * delegate.execute(work, _ as PreviousExecutionContext) >> { UnitOfWork work, PreviousExecutionContext delegateContext ->
            assert delegateContext.inputProperties as Map == ["known": knownSnapshot]
            assert delegateContext.inputFileProperties as Map == ["known-file": knownFileFingerprint, "source-file": sourceFileFingerprint]
        }
        0 * _

        then:
        !skipOutcome.present
    }

    def "skips when work has empty sources"() {
        when:
        step.execute(work, context)

        then:
        _ * context.inputProperties >> ImmutableSortedMap.of("known", knownSnapshot)
        _ * context.inputFileProperties >> ImmutableSortedMap.of("known-file", knownFileFingerprint)
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("known", knownSnapshot),
            ImmutableSortedMap.of("known-file", knownFileFingerprint),
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("source-file", sourceFileFingerprint))

        then:
        1 * sourceFileFingerprint.empty >> true

        then:
        skipOutcome.get() == SHORT_CIRCUITED
    }

    def "skips when work has empty sources and removes previous outputs"() {
        def previousExecutionState = Stub(PreviousExecutionState)
        def previousOutputFile = file("output.txt").createFile()

        when:
        step.execute(work, context)

        then:
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        _ * previousExecutionState.inputProperties >> ImmutableSortedMap.of()
        _ * previousExecutionState.inputFileProperties >> ImmutableSortedMap.of()
        _ * previousExecutionState.outputFilesProducedByWork >> snapshot(previousOutputFile)
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("source-file", sourceFileFingerprint))

        1 * sourceFileFingerprint.empty >> true

        1 * outputChangeListener.beforeOutputChange(rootPaths(previousOutputFile))

        1 * buildOutputCleanupRegistry.isOutputOwnedByBuild(previousOutputFile) >> true
        1 * buildOutputCleanupRegistry.isOutputOwnedByBuild(previousOutputFile.parentFile) >> false

        1 * deleter.delete(previousOutputFile)
        0 * _

        then:
        skipOutcome.get() == EXECUTED_NON_INCREMENTALLY
    }

    // TODO Convert these tests
/*
    def "does not delete previous output when they are not safe to delete"() {
        given:
        def previousFile = temporaryFolder.createFile("output.txt")
        def previousOutputFiles = fingerprint(previousFile)

        when:
        def outcome = skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, previousOutputFiles)

        then:
        outcome.get() == ExecutionOutcome.SHORT_CIRCUITED

        and:
        1 * sourceFiles.empty >> true

        then:
        1 * outputChangeListener.beforeOutputChange(rootPaths(previousFile))

        then:
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> false

        then:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, sourceFiles)

        then:
        previousFile.exists()
        0 * _
    }

    def "does not delete non-empty directories"() {
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

        def previousOutputFiles = fingerprint(outputDir, outputFile)

        def overlappingFile = subDir.file("overlap")
        overlappingFile << "overlapping file"

        when:
        def outcome = skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, previousOutputFiles)

        then:
        outcome.get() == ExecutionOutcome.EXECUTED_NON_INCREMENTALLY

        and:
        1 * sourceFiles.empty >> true

        then:
        1 * outputChangeListener.beforeOutputChange(rootPaths(outputDir, outputFile))

        then:
        _ * cleanupRegistry.isOutputOwnedByBuild(subDir) >> true
        _ * cleanupRegistry.isOutputOwnedByBuild(outputDir) >> true
        _ * cleanupRegistry.isOutputOwnedByBuild(outputDir.parentFile) >> false
        outputFiles.each {
            1 * cleanupRegistry.isOutputOwnedByBuild(it) >> true
        }
        outputDir.exists()
        subDir.exists()
        overlappingFile.exists()
        outputFiles.each {
            assert !it.exists()
        }

        then:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, sourceFiles)

        then:
        0 * _
    }

    def "exception thrown when sourceFiles are empty and deletes previous output, but delete fails"() {
        given:
        def previousFile = temporaryFolder.createFile("output.txt")
        def previousOutputFiles = fingerprint(previousFile)

        when:
        skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, previousOutputFiles)

        then:
        def ex = thrown Exception
        ex.message.contains("Couldn't delete ${previousFile.absolutePath}")

        and:
        1 * sourceFiles.empty >> true

        then:
        1 * outputChangeListener.beforeOutputChange(rootPaths(previousFile))

        then: "deleting the previous file fails"
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> {
            // Convert file into directory here, so that deletion in DefaultEmptySourceTaskSkipper fails
            assert previousFile.delete()
            previousFile.file("subdir/inSubdir.txt") << "inSubdir"
            return true
        }
    }

    def "does not skip when sourceFiles are not empty"() {
        when:
        def outcome = skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, [:])

        then:
        !outcome.present

        and:
        1 * sourceFiles.empty >> false

        then:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, inputFiles)
        0 * _
    }

    def "does not skip when it has not declared any source files"() {
        when:
        def outcome = skipper.skipIfEmptySources(task, false, inputFiles, sourceFiles, [:])

        then:
        !outcome.present

        and:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, inputFiles)
        0 * _
    }

 */

    private static Set<String> rootPaths(File... files) {
        files*.absolutePath as Set
    }

    private def snapshot(File... files) {
        ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "output", fileCollectionSnapshotter.snapshot(TestFiles.fixed(files))
        )
    }
}
