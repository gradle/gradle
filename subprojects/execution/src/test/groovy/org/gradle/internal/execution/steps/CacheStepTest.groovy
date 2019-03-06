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

import com.google.common.collect.ImmutableSortedMap
import org.gradle.caching.internal.command.BuildCacheCommandFactory
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.CacheHandler
import org.gradle.internal.execution.CachingContext
import org.gradle.internal.execution.Context
import org.gradle.internal.execution.CurrentSnapshotResult
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.Step
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.EmptyCurrentFileCollectionFingerprint
import org.gradle.internal.id.UniqueId
import spock.lang.Specification

class CacheStepTest extends Specification {

    def buildCacheController = Mock(BuildCacheController)
    def buildCacheCommandFactory = Mock(BuildCacheCommandFactory)
    def outputChangeListener = Mock(OutputChangeListener)
    def currentBuildId = UniqueId.generate()
    Step<Context, CurrentSnapshotResult> delegateStep = Mock(Step)
    def cacheStep = new CacheStep<CachingContext>(buildCacheController, outputChangeListener, buildCacheCommandFactory, delegateStep)
    def cacheHandler = Mock(CacheHandler)
    def unitOfWork = Mock(UnitOfWork)
    def loadMetadata = Mock(BuildCacheCommandFactory.LoadMetadata)
    def cachedOriginMetadata = Mock(OriginMetadata)
    def context = new CachingContext() {
        @Override
        CacheHandler getCacheHandler() {
            CacheStepTest.this.cacheHandler
        }

        @Override
        Optional<ExecutionStateChanges> getChanges() {
            return Optional.empty()
        }

        @Override
        Optional<String> getRebuildReason() {
            return context.getRebuildReason();
        }

        @Override
        Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
            return context.getAfterPreviousExecutionState();
        }

        @Override
        Optional<BeforeExecutionState> getBeforeExecutionState() {
            return context.getBeforeExecutionState();
        }

        @Override
        UnitOfWork getWork() {
            unitOfWork
        }
    }

    def "loads from cache"() {
        def outputsFromCache = ImmutableSortedMap.of("test", new EmptyCurrentFileCollectionFingerprint())

        when:
        def result = cacheStep.execute(context)
        def originMetadata = result.originMetadata
        def finalOutputs = result.finalOutputs
        then:
        result.outcome.get() == ExecutionOutcome.FROM_CACHE
        result.reused
        originMetadata == cachedOriginMetadata
        finalOutputs == outputsFromCache

        1 * buildCacheController.isEnabled() >> true
        1 * cacheHandler.load(_) >> Optional.of(loadMetadata)
        1 * loadMetadata.originMetadata >> cachedOriginMetadata
        1 * loadMetadata.resultingSnapshots >> outputsFromCache
        0 * _
    }

    def "executes work and stores in cache on cache miss"() {
        def executionResult = new CurrentSnapshotResult() {
            final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = ImmutableSortedMap.of("test", new EmptyCurrentFileCollectionFingerprint())
            final OriginMetadata originMetadata = new OriginMetadata(currentBuildId, 0)
            final Try<ExecutionOutcome> outcome = Try.successful(ExecutionOutcome.EXECUTED_FULLY)
            final boolean reused = false
        }

        when:
        def result = cacheStep.execute(context)

        then:
        result == executionResult
        !result.reused

        1 * buildCacheController.isEnabled() >> true
        1 * cacheHandler.load(_) >> Optional.empty()
        1 * delegateStep.execute(_) >> executionResult
        1 * cacheHandler.store(_)
        0 * _
    }

    def "failures are not stored in the cache"() {
        def failedResult = new CurrentSnapshotResult() {
            final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = ImmutableSortedMap.of("test", new EmptyCurrentFileCollectionFingerprint())
            final OriginMetadata originMetadata = new OriginMetadata(currentBuildId, 0)
            final Try<ExecutionOutcome> outcome = Try.failure(new RuntimeException("failed"))
            final boolean reused = false
        }

        when:
        def result = cacheStep.execute(context)

        then:
        result == failedResult
        !result.reused

        1 * buildCacheController.isEnabled() >> true
        1 * cacheHandler.load(_) >> Optional.empty()
        1 * delegateStep.execute(_) >> failedResult
        _ * unitOfWork.displayName >> "Display name"
        0 * cacheHandler.store(_)
        0 * _
    }

    def "executes when caching is disabled"() {
        def executionResult = new CurrentSnapshotResult() {
            final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = ImmutableSortedMap.of("test", new EmptyCurrentFileCollectionFingerprint())
            final OriginMetadata originMetadata = new OriginMetadata(currentBuildId, 0)
            final Try<ExecutionOutcome> outcome = Try.successful(ExecutionOutcome.EXECUTED_FULLY)
            final boolean reused = false
        }
        when:
        def result = cacheStep.execute(context)
        then:
        result == executionResult

        1 * buildCacheController.isEnabled() >> false
        1 * delegateStep.execute(_) >> executionResult
        0 * _
    }
}
