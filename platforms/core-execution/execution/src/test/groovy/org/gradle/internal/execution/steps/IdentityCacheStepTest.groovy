/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.cache.Cache
import org.gradle.cache.ManualEvictionInMemoryCache
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.ExecutionOutputState
import org.gradle.internal.operations.BuildOperationProgressEventEmitter

class IdentityCacheStepTest extends StepSpec<IdentityContext> {
    Cache<UnitOfWork.Identity, ExecutionEngine.IdentityCacheResult<Object>> cache = new ManualEvictionInMemoryCache<>()
    def progressEventEmitter = Mock(BuildOperationProgressEventEmitter)

    def step = new IdentityCacheStep<>(progressEventEmitter, delegate)

    def "executes when no cached output exists"() {
        def delegateOutput = Mock(Object)
        def delegateResult = Mock(WorkspaceResult)
        def originMetadata = Stub(OriginMetadata) {
            buildInvocationId >> "123245"
        }

        def execution = step.executeDeferred(work, context, cache)

        when:
        def cacheResult = execution.completeAndGet()

        then:
        cacheResult.get() == delegateOutput

        delegateResult.getOutputAs(_ as Class) >> Try.successful(delegateOutput)
        delegateResult.reusedOutputOriginMetadata >> Optional.of(originMetadata)

        1 * delegate.execute(work, context) >> delegateResult
        1 * progressEventEmitter.emitNowIfCurrent({ it ->
            it.originBuildInvocationId == originMetadata.buildInvocationId
        })
        0 * _
    }

    def "returns origin metadata of current build when not re-used"() {
        def delegateOutput = Mock(Object)
        def delegateResult = Mock(WorkspaceResult)
        def originMetadata = Stub(OriginMetadata) {
            buildInvocationId >> "12345"
        }
        def executionOutputState = Mock(ExecutionOutputState)

        def execution = step.executeDeferred(work, context, cache)

        when:
        def cacheResult = execution.completeAndGet()

        then:
        cacheResult.get() == delegateOutput

        delegateResult.getOutputAs(_ as Class) >> Try.successful(delegateOutput)
        delegateResult.reusedOutputOriginMetadata >> Optional.empty()
        delegateResult.afterExecutionOutputState >> Optional.of(executionOutputState)
        executionOutputState.originMetadata >> originMetadata

        1 * delegate.execute(work, context) >> delegateResult
        1 * progressEventEmitter.emitNowIfCurrent({ it.originBuildInvocationId == originMetadata.buildInvocationId })
        0 * _
    }

    def "returns no origin metadata when execution has no output state"() {
        def delegateOutput = Mock(Object)
        def delegateResult = Mock(WorkspaceResult)

        def execution = step.executeDeferred(work, context, cache)

        when:
        def cacheResult = execution.completeAndGet()

        then:
        cacheResult.get() == delegateOutput

        delegateResult.getOutputAs(_ as Class) >> Try.successful(delegateOutput)
        delegateResult.reusedOutputOriginMetadata >> Optional.empty()
        delegateResult.afterExecutionOutputState >> Optional.empty()

        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    def "returns cached output when exists"() {
        def cachedResult = Try.successful(Mock(Object))
        def returnedOriginMetadata = Stub(OriginMetadata) {
            buildInvocationId >> "12345"
        }
        def identityCacheResult = Stub(ExecutionEngine.IdentityCacheResult) {
            result >> cachedResult
            originMetadata >> Optional.of(returnedOriginMetadata)
        }
        cache.put(identity, identityCacheResult)

        def execution = step.executeDeferred(work, context, cache)

        when:
        def actualResult = execution.completeAndGet()

        then:
        actualResult == cachedResult
        0 * _
    }
}
