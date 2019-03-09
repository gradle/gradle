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

package org.gradle.internal.execution.steps

import org.gradle.caching.internal.command.BuildCacheCommandFactory
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.CacheHandler
import org.gradle.internal.execution.CachingContext
import org.gradle.internal.execution.CurrentSnapshotResult
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.OutputChangeListener

class CacheStepTest extends StepSpec implements FingerprinterFixture {
    def buildCacheController = Mock(BuildCacheController)
    def buildCacheCommandFactory = Mock(BuildCacheCommandFactory)
    def outputChangeListener = Mock(OutputChangeListener)
    def step = new CacheStep<CachingContext>(buildCacheController, outputChangeListener, buildCacheCommandFactory, delegate)
    def cacheHandler = Mock(CacheHandler)
    def loadMetadata = Mock(BuildCacheCommandFactory.LoadMetadata)

    def delegateResult = Mock(CurrentSnapshotResult)
    def context = Mock(CachingContext)

    def "loads from cache"() {
        def cachedOriginMetadata = Mock(OriginMetadata)
        def outputsFromCache = fingerprintsOf("test": [])

        when:
        def result = step.execute(context)

        then:
        result.outcome.get() == ExecutionOutcome.FROM_CACHE
        result.reused
        result.originMetadata == cachedOriginMetadata
        result.finalOutputs == outputsFromCache

        1 * buildCacheController.isEnabled() >> true
        1 * context.cacheHandler >> cacheHandler
        1 * cacheHandler.load(_) >> Optional.of(loadMetadata)
        1 * loadMetadata.originMetadata >> cachedOriginMetadata
        1 * loadMetadata.resultingSnapshots >> outputsFromCache
        0 * _
    }

    def "executes work and stores in cache on cache miss"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * buildCacheController.isEnabled() >> true
        1 * context.cacheHandler >> cacheHandler
        1 * cacheHandler.load(_) >> Optional.empty()
        1 * delegate.execute(context) >> delegateResult
        1 * delegateResult.outcome >> Try.successful(ExecutionOutcome.EXECUTED_NON_INCREMENTALLY)
        1 * cacheHandler.store(_)
        0 * _
    }

    def "failures are not stored in the cache"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        !result.reused

        1 * buildCacheController.isEnabled() >> true
        1 * context.cacheHandler >> cacheHandler
        1 * cacheHandler.load(_) >> Optional.empty()
        1 * delegate.execute(context) >> delegateResult
        1 * delegateResult.outcome >> Try.failure(new RuntimeException("failure"))
        1 * context.work >> work
        1 * work.displayName >> "Display name"
        0 * cacheHandler.store(_)
        0 * _
    }

    def "executes when caching is disabled"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        !result.reused

        1 * buildCacheController.isEnabled() >> false
        1 * delegate.execute(_) >> delegateResult
        0 * _
    }
}
