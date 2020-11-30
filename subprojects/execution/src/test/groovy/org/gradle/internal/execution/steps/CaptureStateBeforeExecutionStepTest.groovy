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
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.history.OverlappingOutputDetector
import org.gradle.internal.execution.impl.DefaultInputFingerprinter
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.impl.ImplementationSnapshot

import static org.gradle.internal.execution.UnitOfWork.OverlappingOutputHandling.DETECT_OVERLAPS
import static org.gradle.internal.execution.UnitOfWork.OverlappingOutputHandling.IGNORE_OVERLAPS

class CaptureStateBeforeExecutionStepTest extends StepSpec<ValidationContext> {

    def classloaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def outputSnapshotter = Mock(OutputSnapshotter)
    def inputFingerprinter = Mock(InputFingerprinter)
    def implementationSnapshot = ImplementationSnapshot.of("MyWorkClass", HashCode.fromInt(1234))
    def overlappingOutputDetector = Mock(OverlappingOutputDetector)
    def executionHistoryStore = Mock(ExecutionHistoryStore)

    def step = new CaptureStateBeforeExecutionStep(buildOperationExecutor, classloaderHierarchyHasher, inputFingerprinter, outputSnapshotter, overlappingOutputDetector, delegate)

    @Override
    protected ValidationContext createContext() {
        Stub(ValidationContext) {
            getInputProperties() >> ImmutableSortedMap.of()
            getInputFileProperties() >> ImmutableSortedMap.of()
        }
    }

    def setup() {
        _ * work.history >> Optional.of(executionHistoryStore)
    }

    def "no state is captured when task history is not maintained"() {
        when:
        step.execute(work, context)
        then:
        assertNoOperation()
        _ * work.history >> Optional.empty()
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            assert !delegateContext.beforeExecutionState.present
        }
        0 * _
    }

    def "implementations are snapshotted"() {
        def additionalImplementations = [
            ImplementationSnapshot.of("FirstAction", HashCode.fromInt(2345)),
            ImplementationSnapshot.of("SecondAction", HashCode.fromInt(3456))
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

        assertOperationForInputsBeforeExecution()
    }

    def "input properties are snapshotted"() {
        def knownSnapshot = Mock(ValueSnapshot)
        def knownFileFingerprint = Mock(CurrentFileCollectionFingerprint)
        def inputSnapshot = Mock(ValueSnapshot)
        def inputFileFingerprint = Mock(CurrentFileCollectionFingerprint)

        when:
        step.execute(work, context)

        then:
        _ * context.inputProperties >> ImmutableSortedMap.of("known", knownSnapshot)
        _ * context.inputFileProperties >> ImmutableSortedMap.of("known-file", knownFileFingerprint)
        1 * inputFingerprinter.fingerprintInputProperties(
            work,
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("known", knownSnapshot),
            ImmutableSortedMap.of("known-file", knownFileFingerprint),
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            ImmutableSortedMap.of("input", inputSnapshot),
            ImmutableSortedMap.of("input-file", inputFileFingerprint))
        interaction { snapshotState() }
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            def state = delegateContext.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.inputProperties as Map == ["known": knownSnapshot, "input": inputSnapshot]
            assert state.inputFileProperties as Map == ["known-file": knownFileFingerprint, "input-file": inputFileFingerprint]
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "output file properties are snapshotted"() {
        def outputDirSnapshot = Mock(FileSystemSnapshot)

        when:
        step.execute(work, context)

        then:
        _ * outputSnapshotter.snapshotOutputs(work, _) >> ImmutableSortedMap.<String, FileSystemSnapshot>of("outputDir", outputDirSnapshot)
        interaction { snapshotState() }
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            def state = delegateContext.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.outputFileLocationSnapshots == ImmutableSortedMap.of('outputDir', outputDirSnapshot)
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    def "detects overlapping outputs when instructed"() {
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)
        def afterPreviousOutputSnapshot = Mock(FileSystemSnapshot)
        def afterPreviousOutputSnapshots = ImmutableSortedMap.of("outputDir", afterPreviousOutputSnapshot)
        def beforeExecutionOutputSnapshot = Mock(FileSystemSnapshot)
        def beforeExecutionOutputSnapshots = ImmutableSortedMap.of("outputDir", beforeExecutionOutputSnapshot)

        when:
        step.execute(work, context)
        then:
        _ * context.afterPreviousExecutionState >> Optional.of(afterPreviousExecutionState)
        1 * afterPreviousExecutionState.inputProperties >> ImmutableSortedMap.of()
        1 * afterPreviousExecutionState.outputFilesProducedByWork >> afterPreviousOutputSnapshots
        _ * outputSnapshotter.snapshotOutputs(work, _) >> beforeExecutionOutputSnapshots

        _ * work.overlappingOutputHandling >> DETECT_OVERLAPS
        1 * overlappingOutputDetector.detect(afterPreviousOutputSnapshots, beforeExecutionOutputSnapshots) >> null

        interaction { snapshotState() }
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            def state = delegateContext.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.outputFileLocationSnapshots == beforeExecutionOutputSnapshots
        }
        0 * _

        assertOperationForInputsBeforeExecution()
    }

    void snapshotState() {
        _ * context.afterPreviousExecutionState >> Optional.empty()
        _ * work.visitImplementations(_ as UnitOfWork.ImplementationVisitor) >> { UnitOfWork.ImplementationVisitor visitor ->
            visitor.visitImplementation(implementationSnapshot)
        }
        _ * inputFingerprinter.fingerprintInputProperties(work, _, _, _, _) >> new DefaultInputFingerprinter.InputFingerprints(ImmutableSortedMap.of(), ImmutableSortedMap.of())
        _ * work.overlappingOutputHandling >> IGNORE_OVERLAPS
        _ * outputSnapshotter.snapshotOutputs(work, _) >> ImmutableSortedMap.of()
        _ * context.history >> Optional.of(executionHistoryStore)
    }

    private void assertOperationForInputsBeforeExecution() {
        withOnlyOperation(CaptureStateBeforeExecutionStep.Operation) {
            assert it.descriptor.displayName == "Snapshot inputs and outputs before executing job ':test'"
            assert it.result == CaptureStateBeforeExecutionStep.Operation.Result.INSTANCE
        }
    }
}
