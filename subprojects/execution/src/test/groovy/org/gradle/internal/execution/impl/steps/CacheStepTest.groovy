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
import org.gradle.caching.internal.command.BuildCacheCommandFactory
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.execution.CacheHandler
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.fingerprint.impl.EmptyCurrentFileCollectionFingerprint
import org.gradle.internal.id.UniqueId
import spock.lang.Specification

class CacheStepTest extends Specification {

    def buildCacheController = Mock(BuildCacheController)
    def buildCacheCommandFactory = Mock(BuildCacheCommandFactory)
    def outputChangeListener = Mock(OutputChangeListener)
    def currentBuildId = UniqueId.generate()
    def cacheStep = new CacheStep<CachingContext>(buildCacheController, outputChangeListener, buildCacheCommandFactory,
        new SnapshotOutputStep<CachingContext>(currentBuildId,
            new CatchExceptionStep<Context>(
                new ExecuteStep(outputChangeListener)
            )
        )
    )
    def cacheHandler = Mock(CacheHandler)
    def unitOfWork = Mock(UnitOfWork)
    def loadMetadata = Mock(BuildCacheCommandFactory.LoadMetadata)
    def cachedOriginMetadata = Mock(OriginMetadata)
    def context = Stub(CachingContext) {
        getCacheHandler() >> cacheHandler
        getWork() >> unitOfWork
    }

    def "loads from cache"() {
        def outputsFromCache = ImmutableSortedMap.naturalOrder().put("test", new EmptyCurrentFileCollectionFingerprint()).build()

        when:
        def result = cacheStep.execute(context)
        def originMetadata = result.originMetadata
        def finalOutputs = result.finalOutputs
        then:
        result.outcome == ExecutionOutcome.FROM_CACHE
        result.failure == null
        originMetadata == cachedOriginMetadata
        finalOutputs == outputsFromCache

        1 * cacheHandler.load(_) >> Optional.of(loadMetadata)
        1 * loadMetadata.originMetadata >> cachedOriginMetadata
        1 * loadMetadata.resultingSnapshots >> outputsFromCache
        0 * _
    }

    def "executes work and stores in cache on cache miss"() {
        def snapshottedOutputs = ImmutableSortedMap.naturalOrder().put("test", new EmptyCurrentFileCollectionFingerprint()).build()

        when:
        def result = cacheStep.execute(context)

        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.failure == null
        result.originMetadata.buildInvocationId == currentBuildId
        result.finalOutputs == snapshottedOutputs

        1 * cacheHandler.load(_) >> Optional.empty()
        1 * unitOfWork.changingOutputs >> Optional.empty()
        1 * outputChangeListener.beforeOutputChange()
        1 * unitOfWork.execute() >> true
        1 * unitOfWork.snapshotAfterOutputsGenerated() >> snapshottedOutputs
        1 * unitOfWork.markExecutionTime() >> 0
        1 * cacheHandler.store(_)
        0 * _
    }

    def "failures are not stored in the cache"() {
        def snapshottedOutputs = ImmutableSortedMap.naturalOrder().put("test", new EmptyCurrentFileCollectionFingerprint()).build()
        def failure = new RuntimeException("failed")

        when:
        def result = cacheStep.execute(context)

        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.failure.cause == failure
        result.originMetadata.buildInvocationId == currentBuildId
        result.finalOutputs == snapshottedOutputs

        1 * cacheHandler.load(_) >> Optional.empty()
        1 * unitOfWork.changingOutputs >> Optional.empty()
        1 * outputChangeListener.beforeOutputChange()
        1 * unitOfWork.execute() >> {
            throw failure
        }
        1 * unitOfWork.snapshotAfterOutputsGenerated() >> snapshottedOutputs
        1 * unitOfWork.markExecutionTime() >> 0
        _ * unitOfWork.displayName >> "Display name"
        0 * cacheHandler.store(_)
        0 * _
    }
}
