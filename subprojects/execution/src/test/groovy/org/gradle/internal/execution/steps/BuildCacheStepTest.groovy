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

import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory
import org.gradle.internal.execution.caching.CachingState
import org.gradle.internal.execution.history.AfterExecutionState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.file.Deleter

import java.time.Duration

import static org.gradle.internal.execution.ExecutionEngine.Execution
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.FROM_CACHE

class BuildCacheStepTest extends StepSpec<IncrementalChangesContext> implements SnapshotterFixture {
    def buildCacheController = Mock(BuildCacheController)

    def beforeExecutionState = Stub(BeforeExecutionState)

    def cacheKeyHashCode = "30a042b90a"
    def cacheKey = Stub(BuildCacheKey) {
        hashCode >> cacheKeyHashCode
    }
    def loadMetadata = Mock(BuildCacheLoadResult)
    def deleter = Mock(Deleter)
    def outputChangeListener = Mock(OutputChangeListener)

    def step = new BuildCacheStep(buildCacheController, deleter, outputChangeListener, delegate)
    def delegateResult = Mock(AfterExecutionResult)

    def "loads from cache"() {
        def cachedOriginMetadata = Stub(OriginMetadata)
        cachedOriginMetadata.executionTime >> Duration.ofSeconds(1)
        def outputsFromCache = snapshotsOf("test": [])
        def localStateFile = file("local-state.txt") << "local state"

        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == FROM_CACHE
        result.afterExecutionState.get().reused
        result.afterExecutionState.get().originMetadata == cachedOriginMetadata
        result.afterExecutionState.get().outputFilesProducedByWork == outputsFromCache

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheController.load(cacheKey, _) >> Optional.of(loadMetadata)

        then:
        _ * work.visitOutputs(_ as File, _ as UnitOfWork.OutputVisitor) >> { File workspace, UnitOfWork.OutputVisitor visitor ->
            visitor.visitLocalState(localStateFile)
        }
        1 * outputChangeListener.invalidateCachesFor([localStateFile.getAbsolutePath()])
        1 * deleter.deleteRecursively(_) >> { File root ->
            assert root == localStateFile
            return true
        }

        then:
        1 * loadMetadata.originMetadata >> cachedOriginMetadata
        1 * loadMetadata.resultingSnapshots >> outputsFromCache

        0 * _
    }

    def "executes work and stores in cache on cache miss"() {
        given:
        def execution = Mock(Execution)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheController.load(cacheKey, _) >> Optional.empty()

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.execution >> Try.successful(execution)
        1 * execution.canStoreOutputsInCache() >> true

        then:
        interaction { outputStored {} }
        0 * _
    }

    def "fails after #exceptionName unpack failure with descriptive error"() {
        def loadedOutputFile = file("output.txt")
        def loadedOutputDir = file("output")
        def failure = new RuntimeException("unpack failure")

        when:
        step.execute(work, context)

        then:
        def ex = thrown Exception
        ex.message == "Failed to load cache entry $cacheKeyHashCode for job ':test': unpack failure"
        ex.cause == failure

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheController.load(cacheKey, _) >> { BuildCacheKey key, CacheableEntity entity ->
            loadedOutputFile << "output"
            loadedOutputDir.mkdirs()
            loadedOutputDir.file("output.txt") << "output"
            throw failure
        }

        then:
        0 * _
    }

    def "does not store untracked result in cache"() {
        given:
        def execution = Mock(Execution)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheController.load(cacheKey, _) >> Optional.empty()

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.execution >> Try.successful(execution)
        1 * delegateResult.afterExecutionState >> Optional.empty()
        1 * execution.canStoreOutputsInCache() >> true

        then:
        0 * buildCacheController.store(_)
        0 * _
    }

    def "does not store result of failed execution in cache"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheController.load(cacheKey, _) >> Optional.empty()

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.execution >> Try.failure(new RuntimeException("failure"))

        then:
        0 * buildCacheController.store(_)
        0 * _
    }

    def "does not load but stores when loading is disabled"() {
        given:
        def execution = Mock(Execution)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> false

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.execution >> Try.successful(execution)
        1 * execution.canStoreOutputsInCache() >> true

        then:
        interaction { outputStored {} }
        0 * _
    }

    def "fails when cache backend throws exception while storing cached result"() {
        given:
        def execution = Mock(Execution)
        def failure = new RuntimeException("store failure")

        when:
        step.execute(work, context)

        then:
        def ex = thrown Exception
        ex.message == "Failed to store cache entry $cacheKeyHashCode for job ':test': store failure"
        ex.cause == failure

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> false

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.execution >> Try.successful(execution)
        1 * execution.canStoreOutputsInCache() >> true

        then:
        interaction { outputStored { throw failure } }
        0 * _
    }

    def "executes and doesn't store when caching is disabled"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        _ * context.cachingState >> CachingState.disabledWithoutInputs(new CachingDisabledReason(CachingDisabledReasonCategory.UNKNOWN, "Unknown"))
        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    def "executes and doesn't store when storing is disabled"() {
        given:
        def execution = Mock(Execution)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        interaction { withValidCacheKey() }

        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.execution >> Try.successful(execution)
        1 * execution.canStoreOutputsInCache() >> false
        0 * _
    }

    private void withValidCacheKey() {
        _ * context.cachingState >> CachingState.enabled(cacheKey, beforeExecutionState)
    }

    private void outputStored(Closure storeResult) {
        def originMetadata = Mock(OriginMetadata)
        def outputFilesProducedByWork = snapshotsOf("test": [])

        1 * delegateResult.afterExecutionState >> Optional.of(Mock(AfterExecutionState) {
            1 * getOutputFilesProducedByWork() >> outputFilesProducedByWork
            1 * getOriginMetadata() >> originMetadata
        })
        1 * originMetadata.executionTime >> Duration.ofMillis(123L)
        1 * buildCacheController.store(cacheKey, _, outputFilesProducedByWork, Duration.ofMillis(123L)) >> { storeResult() }
    }
}
