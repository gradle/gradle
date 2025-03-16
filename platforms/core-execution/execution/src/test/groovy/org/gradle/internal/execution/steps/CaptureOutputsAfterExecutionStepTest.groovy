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
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory
import org.gradle.internal.execution.caching.CachingState
import org.gradle.internal.execution.caching.impl.DefaultBuildCacheKey
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.hash.HashCode
import org.gradle.internal.id.UniqueId
import org.gradle.internal.snapshot.FileSystemSnapshot

import java.time.Duration

import static org.gradle.internal.hash.TestHashCodes.hashCodeFrom

class CaptureOutputsAfterExecutionStepTest extends StepSpec<TestCachingContext> {

    def buildInvocationScopeId = UniqueId.generate()
    def beforeExecutionState = Mock(BeforeExecutionState)
    def outputSnapshotter = Mock(OutputSnapshotter)
    def outputFilter = Mock(AfterExecutionOutputFilter)
    def delegateResult = Stub(Result)

    def step = new CaptureOutputsAfterExecutionStep<>(buildOperationRunner, buildInvocationScopeId, outputSnapshotter, outputFilter, delegate)

    def "no state is captured if cache key calculated state is unavailable"() {
        def delegateDuration = Duration.ofMillis(123)
        delegateResult.duration >> delegateDuration

        when:
        def result = step.execute(work, context)
        then:
        !result.afterExecutionOutputState.present
        result.duration == delegateDuration
        assertNoOperation()

        1 * delegate.execute(work, _) >> delegateResult
        _ * context.cachingState >> CachingState.disabledWithoutInputs(new CachingDisabledReason(CachingDisabledReasonCategory.UNKNOWN, "Unknown"))
        0 * _
    }

    def "fails if snapshotting outputs fail"() {
        def delegateDuration = Duration.ofMillis(123)
        def failure = new OutputSnapshotter.OutputFileSnapshottingException("output", new IOException("Error")) {}
        delegateResult.duration >> delegateDuration

        when:
        step.execute(work, context)

        then:
        def ex = thrown RuntimeException
        ex == failure
        assertOperation(ex)

        1 * delegate.execute(work, _) >> delegateResult

        then:
        _ * context.cachingState >> CachingState.enabled(new DefaultBuildCacheKey(hashCodeFrom(1234)), beforeExecutionState)
        1 * outputSnapshotter.snapshotOutputs(work, _) >> { throw failure }
        0 * _
    }

    def "captured outputs are filtered"() {
        def delegateDuration = Duration.ofMillis(123)
        def outputSnapshots = ImmutableSortedMap.<String, FileSystemSnapshot>of(
            "outputDir", Mock(FileSystemSnapshot),
            "outputFile", Mock(FileSystemSnapshot),
        )
        def filteredOutputSnapshots = ImmutableSortedMap.<String, FileSystemSnapshot>of(
            "outputDir", Mock(FileSystemSnapshot)
        )
        def buildCacheKey = HashCode.fromString("0123456789abcdef")
        delegateResult.duration >> delegateDuration

        when:
        def result = step.execute(work, context)
        then:
        result.afterExecutionOutputState.get().outputFilesProducedByWork == filteredOutputSnapshots
        result.duration == delegateDuration
        result.afterExecutionOutputState.get().originMetadata.buildInvocationId == buildInvocationScopeId.asString()
        result.afterExecutionOutputState.get().originMetadata.buildCacheKey == buildCacheKey
        result.afterExecutionOutputState.get().originMetadata.executionTime >= result.duration
        !result.afterExecutionOutputState.get().reused
        assertOperation()

        1 * delegate.execute(work, _) >> delegateResult

        then:
        1 * outputSnapshotter.snapshotOutputs(work, _) >> outputSnapshots
        1 * outputFilter.filterOutputs(context, beforeExecutionState, outputSnapshots) >> filteredOutputSnapshots
        _ * context.cachingState >> CachingState.enabled(new DefaultBuildCacheKey(buildCacheKey), beforeExecutionState)
        0 * _
    }

    private void assertOperation(Throwable expectedFailure = null) {
        if (expectedFailure == null) {
            assertSuccessfulOperation(CaptureOutputsAfterExecutionStep.Operation, "Snapshot outputs after executing job ':test'", CaptureOutputsAfterExecutionStep.Operation.Result.INSTANCE)
        } else {
            assertFailedOperation(CaptureOutputsAfterExecutionStep.Operation, "Snapshot outputs after executing job ':test'", expectedFailure)
        }
    }
}
