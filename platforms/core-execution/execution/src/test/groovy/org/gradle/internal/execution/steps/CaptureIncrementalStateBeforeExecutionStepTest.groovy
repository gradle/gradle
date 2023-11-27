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
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.OverlappingOutputDetector
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.snapshot.FileSystemSnapshot

import static org.gradle.internal.execution.UnitOfWork.OverlappingOutputHandling.DETECT_OVERLAPS

class CaptureIncrementalStateBeforeExecutionStepTest extends AbstractCaptureStateBeforeExecutionStepTest<PreviousExecutionContext> {

    def outputSnapshotter = Mock(OutputSnapshotter)
    def overlappingOutputDetector = Mock(OverlappingOutputDetector)

    AbstractCaptureStateBeforeExecutionStep<PreviousExecutionContext, CachingResult> step
        = new CaptureIncrementalStateBeforeExecutionStep(buildOperationExecutor, classloaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector, delegate)

    def setup() {
        _ * work.inputFingerprinter >> inputFingerprinter
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

    @Override
    void snapshotState() {
        super.snapshotState()
        _ * outputSnapshotter.snapshotOutputs(work, _) >> ImmutableSortedMap.of()
    }
}
