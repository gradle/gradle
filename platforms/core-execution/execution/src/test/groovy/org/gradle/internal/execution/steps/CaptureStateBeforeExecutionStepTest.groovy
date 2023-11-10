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

import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.OverlappingOutputDetector
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.impl.DefaultInputFingerprinter
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.impl.ImplementationSnapshot

import static org.gradle.internal.execution.UnitOfWork.OverlappingOutputHandling.DETECT_OVERLAPS
import static org.gradle.internal.execution.UnitOfWork.OverlappingOutputHandling.IGNORE_OVERLAPS

class CaptureStateBeforeExecutionStepTest extends StepSpec<PreviousExecutionContext> {

    def classloaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def outputSnapshotter = Mock(OutputSnapshotter)
    def inputFingerprinter = Mock(InputFingerprinter)
    def implementationSnapshot = ImplementationSnapshot.of("MyWorkClass", TestHashCodes.hashCodeFrom(1234))
    def overlappingOutputDetector = Mock(OverlappingOutputDetector)

    def step = new CaptureStateBeforeExecutionStep(buildOperationExecutor, classloaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector, delegate)

    def setup() {
        _ * work.inputFingerprinter >> inputFingerprinter
    }

    def "no state is captured when instructed to skip"() {
        when:
        step.execute(work, context)
        then:
        assertNoOperation()
        _ * context.shouldCaptureBeforeExecutionState() >> false
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            assert !delegateContext.beforeExecutionState.present
        }
        0 * _
    }

    def "implementations are snapshotted"() {
        def additionalImplementations = [
            ImplementationSnapshot.of("FirstAction", TestHashCodes.hashCodeFrom(2345)),
            ImplementationSnapshot.of("SecondAction", TestHashCodes.hashCodeFrom(3456))
        ]

        when:
        step.execute(work, context)

        then:
        _ * work.visitImplementations(_) >> { UnitOfWork.ImplementationVisitor visitor ->
            visitor.visitImplementation(implementationSnapshot)
            additionalImplementations.each {
                visitor.visitImplementation(it)
            }
        }
        interaction { snapshotState() }
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            def state = delegateContext.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.implementation == implementationSnapshot
            assert state.additionalImplementations == additionalImplementations
        }
        0 * _

        assertOperation()
    }

    def "input properties are snapshotted"() {
        def knownSnapshot = Mock(ValueSnapshot)
        def knownFileFingerprint = Mock(CurrentFileCollectionFingerprint)
        def inputSnapshot = Mock(ValueSnapshot)
        def inputFileFingerprint = Mock(CurrentFileCollectionFingerprint)
        def knownInputProperties = ImmutableSortedMap.of("known", knownSnapshot)
        def knownInputFileProperties = ImmutableSortedMap.of("known-file", knownFileFingerprint)

        when:
        step.execute(work, context)

        then:
        _ * context.inputProperties >> knownInputProperties
        _ * context.inputFileProperties >> knownInputFileProperties
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            knownInputProperties,
            knownInputFileProperties,
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            knownInputProperties,
            ImmutableSortedMap.of("input", inputSnapshot),
            knownInputFileProperties,
            ImmutableSortedMap.of("input-file", inputFileFingerprint),
            ImmutableSet.of())
        interaction { snapshotState() }
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            def state = delegateContext.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.inputProperties as Map == ["known": knownSnapshot, "input": inputSnapshot]
            assert state.inputFileProperties as Map == ["known-file": knownFileFingerprint, "input-file": inputFileFingerprint]
        }
        0 * _

        assertOperation()
    }

    def "output file properties are snapshotted"() {
        def outputSnapshots = ImmutableSortedMap.<String, FileSystemSnapshot>of("outputDir", Mock(FileSystemSnapshot))

        when:
        step.execute(work, context)

        then:
        _ * outputSnapshotter.snapshotOutputs(work, _) >> outputSnapshots
        interaction { snapshotState() }
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            def state = delegateContext.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.outputFileLocationSnapshots == outputSnapshots
        }
        0 * _

        assertOperation()
    }

    def "fails when input properties cannot be snapshot"() {
        def failure = new InputFingerprinter.InputFileFingerprintingException("input", new IOException("Error"))
        when:
        step.execute(work, context)

        then:
        def ex = thrown RuntimeException
        ex == failure

        _ * context.inputProperties >> ImmutableSortedMap.of()
        _ * context.inputFileProperties >> ImmutableSortedMap.of()
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            _
        ) >> { throw failure }
        interaction { snapshotState() }
        0 * _

        assertOperation(ex)
    }

    def "fails when output file properties cannot be snapshot"() {
        def failure = new OutputSnapshotter.OutputFileSnapshottingException("output", new IOException("Error")) {}
        when:
        step.execute(work, context)

        then:
        def ex = thrown RuntimeException
        ex == failure

        _ * context.inputProperties >> ImmutableSortedMap.of()
        _ * context.inputFileProperties >> ImmutableSortedMap.of()
        1 * outputSnapshotter.snapshotOutputs(work, _) >> { throw failure }
        interaction { snapshotState() }
        0 * _

        assertOperation(ex)
    }

    def "detects overlapping outputs when instructed"() {
        def previousExecutionState = Mock(PreviousExecutionState)
        def previousOutputSnapshot = Mock(FileSystemSnapshot)
        def previousOutputSnapshots = ImmutableSortedMap.of("outputDir", previousOutputSnapshot)
        def beforeExecutionOutputSnapshot = Mock(FileSystemSnapshot)
        def beforeExecutionOutputSnapshots = ImmutableSortedMap.of("outputDir", beforeExecutionOutputSnapshot)

        when:
        step.execute(work, context)
        then:
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        1 * previousExecutionState.inputProperties >> ImmutableSortedMap.of()
        1 * previousExecutionState.inputFileProperties >> ImmutableSortedMap.of()
        1 * previousExecutionState.outputFilesProducedByWork >> previousOutputSnapshots
        _ * outputSnapshotter.snapshotOutputs(work, _) >> beforeExecutionOutputSnapshots

        _ * work.overlappingOutputHandling >> DETECT_OVERLAPS
        1 * overlappingOutputDetector.detect(previousOutputSnapshots, beforeExecutionOutputSnapshots) >> null

        interaction { snapshotState() }
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            def state = delegateContext.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.outputFileLocationSnapshots == beforeExecutionOutputSnapshots
        }
        0 * _

        assertOperation()
    }

    void snapshotState() {
        _ * context.shouldCaptureBeforeExecutionState() >> true
        _ * context.previousExecutionState >> Optional.empty()
        _ * work.visitImplementations(_ as UnitOfWork.ImplementationVisitor) >> { UnitOfWork.ImplementationVisitor visitor ->
            visitor.visitImplementation(implementationSnapshot)
        }
        _ * inputFingerprinter.fingerprintInputProperties(_, _, _, _, _) >> new DefaultInputFingerprinter.InputFingerprints(ImmutableSortedMap.of(), ImmutableSortedMap.of(), ImmutableSortedMap.of(), ImmutableSortedMap.of(), ImmutableSet.of())
        _ * work.overlappingOutputHandling >> IGNORE_OVERLAPS
        _ * outputSnapshotter.snapshotOutputs(work, _) >> ImmutableSortedMap.of()
    }

    private void assertOperation(Throwable expectedFailure = null) {
        if (expectedFailure == null) {
            assertSuccessfulOperation(CaptureStateBeforeExecutionStep.Operation, "Snapshot inputs and outputs before executing job ':test'", CaptureStateBeforeExecutionStep.Operation.Result.INSTANCE)
        } else {
            assertFailedOperation(CaptureStateBeforeExecutionStep.Operation, "Snapshot inputs and outputs before executing job ':test'", expectedFailure)
        }
    }
}
