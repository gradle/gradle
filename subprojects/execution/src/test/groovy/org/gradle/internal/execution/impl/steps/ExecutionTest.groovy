/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.impl.steps

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.BuildCancelledException
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.execution.CacheHandler
import org.gradle.internal.execution.Context
import org.gradle.internal.execution.ExecutionException
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import spock.lang.Specification

import java.time.Duration
import java.util.function.Supplier

class ExecutionTest extends Specification {

    def outputChangeListener = Mock(OutputChangeListener)
    def cancellationToken = new DefaultBuildCancellationToken()
    def executionStep = new CatchExceptionStep<Context>(
        new CancelExecutionStep<Context>(cancellationToken,
            new ExecuteStep(outputChangeListener)
        )
    )

    def "executes the unit of work with outcome: #outcome"() {
        def unitOfWork = new TestUnitOfWork({ -> outcome })
        when:
        def result = executionStep.execute { -> unitOfWork}

        then:
        unitOfWork.executed
        result.outcome.get() == outcome

        1 * outputChangeListener.beforeOutputChange()
        0 * _

        where:
        outcome << [ExecutionOutcome.EXECUTED_FULLY, ExecutionOutcome.EXECUTED_INCREMENTALLY]
    }

    def "reports no work done"() {
        when:
        def result = executionStep.execute { ->
            new TestUnitOfWork({ ->
                return ExecutionOutcome.UP_TO_DATE
            })
        }

        then:
        result.outcome.get() == ExecutionOutcome.UP_TO_DATE

        1 * outputChangeListener.beforeOutputChange()
        0 * _
    }

    def "catches failures"() {
        def failure = new RuntimeException("broken")
        def unitOfWork = new TestUnitOfWork({ ->
            throw failure
        })

        when:
        def result = executionStep.execute { -> unitOfWork }

        then:
        result.outcome.failure.get() instanceof ExecutionException
        result.outcome.failure.get().cause == failure
        result.outcome.failure.get().message.contains(unitOfWork.displayName)

        1 * outputChangeListener.beforeOutputChange()
        0 * _
    }

    def "invalidates only changing outputs"() {
        def changingOutputs = ['some/location']
        def unitOfWork = new TestUnitOfWork({ -> ExecutionOutcome.EXECUTED_FULLY }, changingOutputs)

        when:
        def result = executionStep.execute { -> unitOfWork }

        then:
        result.outcome.get() == ExecutionOutcome.EXECUTED_FULLY

        1 * outputChangeListener.beforeOutputChange(changingOutputs)
        0 * _
    }

    def "fails the execution when build has been cancelled"() {
        def unitOfWork = new TestUnitOfWork({ -> ExecutionOutcome.EXECUTED_FULLY })

        when:
        cancellationToken.cancel()
        def result = executionStep.execute { -> unitOfWork }

        then:
        result.outcome.failure.get() instanceof ExecutionException
        result.outcome.failure.get().cause instanceof BuildCancelledException

        1 * outputChangeListener.beforeOutputChange()
        0 * _
    }

    static class TestUnitOfWork implements UnitOfWork {

        private final Supplier<ExecutionOutcome> work
        private final Iterable<String> changingOutputs

        TestUnitOfWork(Supplier<ExecutionOutcome> work, Iterable<String> changingOutputs = null) {
            this.changingOutputs = changingOutputs
            this.work = work
        }

        boolean executed

        @Override
        ExecutionOutcome execute() {
            executed = true
            return work.get()
        }

        @Override
        Optional<Duration> getTimeout() {
            throw new UnsupportedOperationException()
        }

        @Override
        void visitOutputProperties(OutputPropertyVisitor visitor) {
            throw new UnsupportedOperationException()
        }

        @Override
        long markExecutionTime() {
            throw new UnsupportedOperationException()
        }

        @Override
        void visitLocalState(LocalStateVisitor visitor) {
            throw new UnsupportedOperationException()
        }

        @Override
        void outputsRemovedAfterFailureToLoadFromCache() {
            throw new UnsupportedOperationException()
        }

        @Override
        CacheHandler createCacheHandler() {
            throw new UnsupportedOperationException()
        }

        @Override
        void persistResult(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs, boolean successful, OriginMetadata originMetadata) {
            throw new UnsupportedOperationException()
        }

        @Override
        Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
            throw new UnsupportedOperationException()
        }

        @Override
        Optional<? extends Iterable<String>> getChangingOutputs() {
            Optional.ofNullable(changingOutputs)
        }

        @Override
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            throw new UnsupportedOperationException()
        }

        @Override
        String getIdentity() {
            throw new UnsupportedOperationException()
        }

        @Override
        void visitOutputTrees(CacheableTreeVisitor visitor) {
            throw new UnsupportedOperationException()
        }

        @Override
        String getDisplayName() {
            "Test unit of work"
        }
    }

}
