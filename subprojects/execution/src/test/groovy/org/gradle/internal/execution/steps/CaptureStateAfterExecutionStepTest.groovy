/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.id.UniqueId
import org.gradle.internal.snapshot.FileSystemSnapshot

import java.time.Duration

class CaptureStateAfterExecutionStepTest extends StepSpec<BeforeExecutionContext> {

    def buildInvocationScopeId = UniqueId.generate()
    def outputSnapshotter = Mock(OutputSnapshotter)
    def delegateResult = Mock(Result)

    def step = new CaptureStateAfterExecutionStep(buildOperationExecutor, buildInvocationScopeId, outputSnapshotter, delegate)

    @Override
    protected ValidationFinishedContext createContext() {
        Stub(ValidationFinishedContext) {
            getInputProperties() >> ImmutableSortedMap.of()
            getInputFileProperties() >> ImmutableSortedMap.of()
        }
    }

    def "no state is captured if before execution state is unavailable"() {
        def delegateDuration = Duration.ofMillis(123)

        when:
        def result = step.execute(work, context)
        then:
        !result.afterExecutionState.present
        !result.reused
        result.duration == delegateDuration
        result.originMetadata.buildInvocationId == buildInvocationScopeId.asString()
        result.originMetadata.executionTime >= result.duration

        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.duration >> delegateDuration
        _ * context.beforeExecutionState >> Optional.empty()
        assertNoOperation()
        0 * _
    }

    def "no state is captured if snapshotting outputs fail"() {
        def delegateDuration = Duration.ofMillis(123)
        def failure = new OutputSnapshotter.OutputFileSnapshottingException("output", new IOException("Error")) {}

        when:
        def result = step.execute(work, context)
        then:
        !result.afterExecutionState.present
        !result.reused
        result.duration == delegateDuration
        result.originMetadata.buildInvocationId == buildInvocationScopeId.asString()
        result.originMetadata.executionTime >= result.duration

        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.duration >> delegateDuration
        _ * context.beforeExecutionState >> Optional.of(Mock(BeforeExecutionState) {
            _ * detectedOverlappingOutputs >> Optional.empty()
        })
        1 * outputSnapshotter.snapshotOutputs(work, _) >> { throw failure }
        assertOperation(failure)
        0 * _
    }

    def "non-overlapping outputs are captured"() {
        def delegateDuration = Duration.ofMillis(123)
        def outputSnapshots = ImmutableSortedMap.<String, FileSystemSnapshot>of("outputDir", Mock(FileSystemSnapshot))

        when:
        def result = step.execute(work, context)
        then:
        result.afterExecutionState.get().outputFilesProducedByWork == outputSnapshots
        !result.reused
        result.duration == delegateDuration
        result.originMetadata.buildInvocationId == buildInvocationScopeId.asString()
        result.originMetadata.executionTime >= result.duration

        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.duration >> delegateDuration
        _ * context.beforeExecutionState >> Optional.of(Mock(BeforeExecutionState) {
            _ * detectedOverlappingOutputs >> Optional.empty()
        })
        1 * outputSnapshotter.snapshotOutputs(work, _) >> outputSnapshots
        assertOperation()
        0 * _
    }

    // TODO Add tests for capturing overlapping outputs

    private void assertOperation(Throwable expectedFailure = null) {
        if (expectedFailure == null) {
            assertSuccessfulOperation(CaptureStateAfterExecutionStep.Operation, "Snapshot outputs after executing job ':test'", CaptureStateAfterExecutionStep.Operation.Result.INSTANCE)
        } else {
            assertFailedOperation(CaptureStateAfterExecutionStep.Operation, "Snapshot outputs after executing job ':test'", expectedFailure)
        }
    }
}
