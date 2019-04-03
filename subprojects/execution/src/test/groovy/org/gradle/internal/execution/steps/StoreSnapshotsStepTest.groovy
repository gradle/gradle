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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.CurrentSnapshotResult
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.IncrementalContext
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.impl.ImplementationSnapshot

class StoreSnapshotsStepTest extends StepSpec implements FingerprinterFixture {
    def executionHistoryStore = Mock(ExecutionHistoryStore)

    def originMetadata = Mock(OriginMetadata)
    def implementationSnapshot = ImplementationSnapshot.of("Test", HashCode.fromInt(123))
    def additionalImplementations = ImmutableList.of()
    def inputProperties = ImmutableSortedMap.of()
    def inputFileProperties = ImmutableSortedMap.of()
    def beforeExecutionState = Stub(BeforeExecutionState) {
        getImplementation() >> implementationSnapshot
        getAdditionalImplementations() >> additionalImplementations
        getInputProperties() >> inputProperties
        getInputFileProperties() >> inputFileProperties
    }
    def identity = "identity"

    def outputFile = file("output.txt").text = "output"
    def finalOutputs = fingerprintsOf(output: outputFile)

    def context = Mock(IncrementalContext)
    def step = new StoreSnapshotsStep<IncrementalContext>(delegate)
    def delegateResult = Mock(CurrentSnapshotResult)

    def "output snapshots are stored after successful execution"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        1 * delegate.execute(context) >> delegateResult

        then:
        1 * delegateResult.finalOutputs >> finalOutputs
        1 * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        1 * delegateResult.outcome >> Try.successful(ExecutionOutcome.EXECUTED_NON_INCREMENTALLY)

        then:
        interaction { expectStore(true, finalOutputs) }
        0 * _
    }

    def "output snapshots are stored after failed execution when there's no previous state available"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        1 * delegate.execute(context) >> delegateResult

        then:
        1 * delegateResult.finalOutputs >> finalOutputs
        1 * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        1 * delegateResult.outcome >> Try.failure(new RuntimeException("execution error"))

        then:
        1 * context.afterPreviousExecutionState >> Optional.empty()

        then:
        interaction { expectStore(false, finalOutputs) }
        0 * _
    }

    def "output snapshots are stored after failed execution with changed outputs"() {
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)

        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        1 * delegate.execute(context) >> delegateResult

        then:
        1 * delegateResult.finalOutputs >> finalOutputs
        1 * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        1 * delegateResult.outcome >> Try.failure(new RuntimeException("execution error"))

        then:
        1 * context.afterPreviousExecutionState >> Optional.of(afterPreviousExecutionState)
        1 * afterPreviousExecutionState.outputFileProperties >> fingerprintsOf([:])

        then:
        interaction { expectStore(false, finalOutputs) }
        0 * _
    }

    def "output snapshots are not stored after failed execution with unchanged outputs"() {
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)

        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        1 * delegate.execute(context) >> delegateResult

        then:
        1 * delegateResult.finalOutputs >> finalOutputs
        1 * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        1 * delegateResult.outcome >> Try.failure(new RuntimeException("execution error"))

        then:
        1 * context.afterPreviousExecutionState >> Optional.of(afterPreviousExecutionState)
        1 * afterPreviousExecutionState.outputFileProperties >> finalOutputs
        0 * _
    }

    void expectStore(boolean successful, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs) {
        1 * context.work >> work
        1 * work.executionHistoryStore >> executionHistoryStore
        1 * work.identity >> identity
        1 * delegateResult.originMetadata >> originMetadata
        1 * executionHistoryStore.store(
            identity,
            originMetadata,
            implementationSnapshot,
            additionalImplementations,
            inputProperties,
            inputFileProperties,
            finalOutputs,
            successful
        )
    }
}
