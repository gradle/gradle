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
import org.gradle.internal.execution.AfterPreviousExecutionContext
import org.gradle.internal.execution.BeforeExecutionContext
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy
import org.gradle.internal.fingerprint.overlap.OverlappingOutputDetector
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.snapshot.impl.ImplementationSnapshot

import static org.gradle.internal.execution.UnitOfWork.IdentityKind.NON_IDENTITY
import static org.gradle.internal.execution.UnitOfWork.InputPropertyType.NON_INCREMENTAL
import static org.gradle.internal.execution.UnitOfWork.OverlappingOutputHandling.DETECT_OVERLAPS
import static org.gradle.internal.execution.UnitOfWork.OverlappingOutputHandling.IGNORE_OVERLAPS
import static org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.Operation.Result

class CaptureStateBeforeExecutionStepTest extends StepSpec<AfterPreviousExecutionContext> {

    def classloaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def outputSnapshotter = Mock(OutputSnapshotter)
    def valueSnapshotter = Mock(ValueSnapshotter)
    def implementationSnapshot = ImplementationSnapshot.of("MyWorkClass", HashCode.fromInt(1234))
    def overlappingOutputDetector = Mock(OverlappingOutputDetector)
    def executionHistoryStore = Mock(ExecutionHistoryStore)

    def step = new CaptureStateBeforeExecutionStep(buildOperationExecutor, classloaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector, valueSnapshotter, delegate)

    @Override
    protected AfterPreviousExecutionContext createContext() {
        Stub(AfterPreviousExecutionContext) {
            getInputProperties() >> ImmutableSortedMap.of()
            getInputFileProperties() >> ImmutableSortedMap.of()
        }
    }

    def setup() {
        _ * work.history >> Optional.of(executionHistoryStore)
    }

    def "no state is captured when task history is not maintained"() {
        when:
        step.execute(context)
        then:
        assertNoOperation()
        _ * work.history >> Optional.empty()
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            assert !beforeExecution.beforeExecutionState.present
        }
        0 * _
    }

    def "implementations are snapshotted"() {
        def additionalImplementations = [
            ImplementationSnapshot.of("FirstAction", HashCode.fromInt(2345)),
            ImplementationSnapshot.of("SecondAction", HashCode.fromInt(3456))
        ]

        when:
        step.execute(context)

        then:
        _ * work.visitImplementations(_) >> { UnitOfWork.ImplementationVisitor visitor ->
            visitor.visitImplementation(implementationSnapshot)
            additionalImplementations.each {
                visitor.visitAdditionalImplementation(it)
            }
        }
        interaction { fingerprintInputs() }
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            def state = beforeExecution.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.implementation == implementationSnapshot
            assert state.additionalImplementations == additionalImplementations
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "input properties are snapshotted"() {
        def inputPropertyValue = 'myValue'
        def valueSnapshot = Mock(ValueSnapshot)

        when:
        step.execute(context)
        then:
        _ * work.visitInputProperties(_) >> { UnitOfWork.InputPropertyVisitor visitor ->
            visitor.visitInputProperty("inputString", NON_IDENTITY) { inputPropertyValue }
        }
        1 * valueSnapshotter.snapshot(inputPropertyValue) >> valueSnapshot
        interaction { fingerprintInputs() }
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            def state = beforeExecution.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.inputProperties == ImmutableSortedMap.<String, ValueSnapshot>of('inputString', valueSnapshot)
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "uses previous input property snapshots"() {
        def inputPropertyValue = 'myValue'
        def valueSnapshot = Mock(ValueSnapshot)
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)

        when:
        step.execute(context)
        then:
        _ * context.afterPreviousExecutionState >> Optional.of(afterPreviousExecutionState)
        1 * afterPreviousExecutionState.inputProperties >> ImmutableSortedMap.<String, ValueSnapshot>of("inputString", valueSnapshot)
        1 * afterPreviousExecutionState.outputFileProperties >> ImmutableSortedMap.<String, FileCollectionFingerprint>of()
        _ * work.visitInputProperties(_) >> { UnitOfWork.InputPropertyVisitor visitor ->
            visitor.visitInputProperty("inputString", NON_IDENTITY) { inputPropertyValue }
        }
        1 * valueSnapshotter.snapshot(inputPropertyValue, valueSnapshot) >> valueSnapshot
        interaction { fingerprintInputs() }
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            def state = beforeExecution.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.inputProperties == ImmutableSortedMap.<String, ValueSnapshot>of('inputString', valueSnapshot)
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "input file properties are fingerprinted"() {
        def fingerprint = Mock(CurrentFileCollectionFingerprint)

        when:
        step.execute(context)

        then:
        _ * work.visitInputFileProperties(_) >> { UnitOfWork.InputFilePropertyVisitor visitor ->
            visitor.visitInputFileProperty("inputFile", NON_INCREMENTAL, NON_IDENTITY, "ignored", { -> fingerprint })
        }
        interaction { fingerprintInputs() }
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            def state = beforeExecution.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.inputFileProperties == ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>of('inputFile', fingerprint)
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "output file properties are fingerprinted"() {
        def outputFileSnapshot = Mock(FileSystemSnapshot)

        when:
        step.execute(context)

        then:
        _ * outputSnapshotter.snapshotOutputs(work, _) >> ImmutableSortedMap.<String, FileSystemSnapshot>of("outputDir", outputFileSnapshot)
        interaction { fingerprintInputs() }
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            def state = beforeExecution.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.outputFileProperties == ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>of('outputDir', AbsolutePathFingerprintingStrategy.IGNORE_MISSING.emptyFingerprint)
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "uses before output snapshot when there are no overlapping outputs"() {
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)
        def afterPreviousOutputFingerprint = Mock(FileCollectionFingerprint)
        def outputFileSnapshot = Mock(FileSystemSnapshot)

        when:
        step.execute(context)
        then:
        _ * context.afterPreviousExecutionState >> Optional.of(afterPreviousExecutionState)
        1 * afterPreviousExecutionState.inputProperties >> ImmutableSortedMap.<String, ValueSnapshot>of()
        1 * afterPreviousExecutionState.outputFileProperties >> ImmutableSortedMap.<String, FileCollectionFingerprint>of("outputDir", afterPreviousOutputFingerprint)

        _ * outputSnapshotter.snapshotOutputs(work, _) >> ImmutableSortedMap.<String, FileSystemSnapshot>of("outputDir", outputFileSnapshot)
        1 * outputFileSnapshot.accept(_)

        interaction { fingerprintInputs() }
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            def state = beforeExecution.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.outputFileProperties == ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>of('outputDir', AbsolutePathFingerprintingStrategy.IGNORE_MISSING.emptyFingerprint)
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "detects overlapping outputs when instructed"() {
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)
        def afterPreviousOutputFingerprint = Mock(FileCollectionFingerprint)
        def afterPreviousOutputFingerprints = ImmutableSortedMap.<String, FileCollectionFingerprint> of("outputDir", afterPreviousOutputFingerprint)
        def beforeExecutionOutputFingerprint = Mock(FileSystemSnapshot)
        def beforeExecutionOutputFingerprints = ImmutableSortedMap.<String, FileSystemSnapshot> of("outputDir", beforeExecutionOutputFingerprint)

        when:
        step.execute(context)
        then:
        _ * context.afterPreviousExecutionState >> Optional.of(afterPreviousExecutionState)
        1 * afterPreviousExecutionState.inputProperties >> ImmutableSortedMap.of()
        1 * afterPreviousExecutionState.outputFileProperties >> afterPreviousOutputFingerprints
        _ * outputSnapshotter.snapshotOutputs(work, _) >> beforeExecutionOutputFingerprints

        _ * work.overlappingOutputHandling >> DETECT_OVERLAPS
        1 * overlappingOutputDetector.detect(afterPreviousOutputFingerprints, beforeExecutionOutputFingerprints) >> null

        1 * beforeExecutionOutputFingerprint.accept(_)

        interaction { fingerprintInputs() }
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            def state = beforeExecution.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.outputFileProperties == ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>of('outputDir', AbsolutePathFingerprintingStrategy.IGNORE_MISSING.emptyFingerprint)
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "filters before output snapshot when there are overlapping outputs"() {
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)
        def afterPreviousOutputFingerprint = Mock(FileCollectionFingerprint)
        def afterPreviousOutputFingerprints = ImmutableSortedMap.<String, FileCollectionFingerprint> of("outputDir", afterPreviousOutputFingerprint)
        def beforeExecutionOutputFingerprint = Mock(FileSystemSnapshot)
        def beforeExecutionOutputFingerprints = ImmutableSortedMap.<String, FileSystemSnapshot> of("outputDir", beforeExecutionOutputFingerprint)
        def overlappingOutputs = new OverlappingOutputs("outputDir", "overlapping/path")

        when:
        step.execute(context)
        then:
        _ * context.afterPreviousExecutionState >> Optional.of(afterPreviousExecutionState)
        1 * afterPreviousExecutionState.inputProperties >> ImmutableSortedMap.of()
        1 * afterPreviousExecutionState.outputFileProperties >> afterPreviousOutputFingerprints
        _ * outputSnapshotter.snapshotOutputs(work, _) >> beforeExecutionOutputFingerprints

        _ * work.overlappingOutputHandling >> DETECT_OVERLAPS
        1 * overlappingOutputDetector.detect(afterPreviousOutputFingerprints, beforeExecutionOutputFingerprints) >> overlappingOutputs

        1 * afterPreviousOutputFingerprint.fingerprints >> [:]
        1 * beforeExecutionOutputFingerprint.accept(_)

        interaction { fingerprintInputs() }
        1 * delegate.execute(_) >> { BeforeExecutionContext beforeExecution ->
            def state = beforeExecution.beforeExecutionState.get()
            assert state.detectedOverlappingOutputs.get() == overlappingOutputs
            assert state.outputFileProperties == ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>of('outputDir', AbsolutePathFingerprintingStrategy.IGNORE_MISSING.emptyFingerprint)
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    void fingerprintInputs() {
        _ * context.afterPreviousExecutionState >> Optional.empty()
        _ * work.visitImplementations(_ as UnitOfWork.ImplementationVisitor) >> { UnitOfWork.ImplementationVisitor visitor ->
            visitor.visitImplementation(implementationSnapshot)
        }
        _ * work.visitInputProperties(_ as UnitOfWork.InputPropertyVisitor)
        _ * work.visitInputFileProperties(_ as UnitOfWork.InputFilePropertyVisitor)
        _ * work.overlappingOutputHandling >> IGNORE_OVERLAPS
        _ * outputSnapshotter.snapshotOutputs(work, _) >> ImmutableSortedMap.of()
        _ * context.history >> Optional.of(executionHistoryStore)
    }

    private void assertOperationForInputsBeforeExecution() {
        withOnlyOperation(CaptureStateBeforeExecutionStep.Operation) {
            assert it.descriptor.displayName == "Snapshot inputs and outputs before executing job ':test'"
            assert it.result == Result.INSTANCE
        }
    }
}
