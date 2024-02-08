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

class IdentityCacheStepTest extends StepSpec<IdentityContext> {
    Cache<UnitOfWork.Identity, ExecutionEngine.IdentityCacheResult<Object>> cache = new ManualEvictionInMemoryCache<>()

    def step = new IdentityCacheStep<>(delegate)

    def "executes when no cached output exists"() {
        def delegateOutput = Mock(Object)
        def delegateResult = Mock(WorkspaceResult)
        def originMetadata = Mock(OriginMetadata)

        def execution = step.executeDeferred(work, context, cache)

        when:
        def cacheResult = execution.completeAndGet()
        def actualResult = cacheResult.getResult()

        then:
        actualResult.get() == delegateOutput
        cacheResult.originMetadata.get() == originMetadata

        delegateResult.getOutputAs(_ as Class) >> Try.successful(delegateOutput)
        delegateResult.reusedOutputOriginMetadata >> Optional.of(originMetadata)

        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    def "returns origin metadata of current build when not re-used"() {
        def delegateOutput = Mock(Object)
        def delegateResult = Mock(WorkspaceResult)
        def originMetadata = Mock(OriginMetadata)
        def executionOutputState = Mock(ExecutionOutputState)

        def execution = step.executeDeferred(work, context, cache)

        when:
        def cacheResult = execution.completeAndGet()
        def actualResult = cacheResult.getResult()

        then:
        actualResult.get() == delegateOutput
        cacheResult.originMetadata.get() == originMetadata

        delegateResult.getOutputAs(_ as Class) >> Try.successful(delegateOutput)
        delegateResult.reusedOutputOriginMetadata >> Optional.empty()
        delegateResult.afterExecutionOutputState >> Optional.of(executionOutputState)
        executionOutputState.originMetadata >> originMetadata

        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    def "returns no origin metadata when execution has no output state"() {
        def delegateOutput = Mock(Object)
        def delegateResult = Mock(WorkspaceResult)
        def originMetadata = Mock(OriginMetadata)
        def executionOutputState = Mock(ExecutionOutputState)

        def execution = step.executeDeferred(work, context, cache)

        when:
        def cacheResult = execution.completeAndGet()
        def actualResult = cacheResult.getResult()

        then:
        actualResult.get() == delegateOutput
        !cacheResult.originMetadata.present

        delegateResult.getOutputAs(_ as Class) >> Try.successful(delegateOutput)
        delegateResult.reusedOutputOriginMetadata >> Optional.empty()
        delegateResult.afterExecutionOutputState >> Optional.empty()

        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    def "returns cached output when exists"() {
        def cachedResult = Mock(ExecutionEngine.IdentityCacheResult)
        cache.put(identity, cachedResult)

        def execution = step.executeDeferred(work, context, cache)

        when:
        def actualResult = execution.completeAndGet()

        then:
        actualResult == cachedResult
        0 * _
    }
}
