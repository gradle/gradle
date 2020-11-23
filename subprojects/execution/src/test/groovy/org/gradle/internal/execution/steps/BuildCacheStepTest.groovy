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

import com.google.common.collect.ImmutableList
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.controller.BuildCacheCommandFactory
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.controller.BuildCacheLoadCommand
import org.gradle.caching.internal.controller.BuildCacheStoreCommand
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.ExecutionResult
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory
import org.gradle.internal.execution.caching.CachingState
import org.gradle.internal.file.Deleter

class BuildCacheStepTest extends StepSpec<IncrementalChangesContext> implements SnasphotterFixture {
    def buildCacheController = Mock(BuildCacheController)
    def buildCacheCommandFactory = Mock(BuildCacheCommandFactory)

    def cacheKey = Stub(BuildCacheKey)
    def cachingState = Mock(CachingState)
    def loadMetadata = Mock(BuildCacheCommandFactory.LoadMetadata)
    def deleter = Mock(Deleter)
    def outputChangeListener = Mock(OutputChangeListener)

    def step = new BuildCacheStep(buildCacheController, buildCacheCommandFactory, deleter, outputChangeListener, delegate)
    def delegateResult = Mock(CurrentSnapshotResult)

    @Override
    protected IncrementalChangesContext createContext() {
        Stub(IncrementalChangesContext)
    }

    def loadCommand = Mock(BuildCacheLoadCommand)

    def "loads from cache"() {
        def cachedOriginMetadata = Mock(OriginMetadata)
        def outputsFromCache = snapshotsOf("test": [])
        def localStateFile = file("local-state.txt") << "local state"

        when:
        def result = step.execute(work, context)

        then:
        result.executionResult.get().outcome == ExecutionOutcome.FROM_CACHE
        result.reused
        result.originMetadata == cachedOriginMetadata
        result.outputFilesProduceByWork == outputsFromCache

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheCommandFactory.createLoad(cacheKey, _) >> loadCommand
        1 * buildCacheController.load(loadCommand) >> Optional.of(loadMetadata)

        then:
        _ * work.visitOutputs(_ as File, _ as UnitOfWork.OutputVisitor) >> { File workspace, UnitOfWork.OutputVisitor visitor ->
            visitor.visitLocalState(localStateFile)
        }
        1 * outputChangeListener.beforeOutputChange([localStateFile.getAbsolutePath()])
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
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheCommandFactory.createLoad(cacheKey, _) >> loadCommand
        1 * buildCacheController.load(loadCommand) >> Optional.empty()

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.executionResult >> Try.successful(Mock(ExecutionResult))

        then:
        interaction { outputStored {} }
        0 * _
    }

    def "fails after unpack failure"() {
        def failure = new RuntimeException("unpack failure")
        def loadedOutputFile = file("output.txt")
        def loadedOutputDir = file("output")

        when:
        step.execute(work, context)

        then:
        def ex = thrown Exception
        ex.message == "Failed to load cache entry for job ':test'"
        ex.cause == failure

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheCommandFactory.createLoad(cacheKey, _) >> loadCommand
        1 * buildCacheController.load(loadCommand) >> { BuildCacheLoadCommand command ->
            loadedOutputFile << "output"
            loadedOutputDir.mkdirs()
            loadedOutputDir.file("output.txt") << "output"
            throw failure
        }

        then:
        0 * _
    }

    def "does not store result of failed execution in cache"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        !result.reused

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> true
        1 * buildCacheCommandFactory.createLoad(cacheKey, _) >> loadCommand
        1 * buildCacheController.load(loadCommand) >> Optional.empty()

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.executionResult >> Try.failure(new RuntimeException("failure"))

        then:
        0 * buildCacheController.store(_)
        0 * _
    }

    def "does not load but stores when loading is disabled"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> false

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.executionResult >> Try.successful(Mock(ExecutionResult))

        then:
        interaction { outputStored {} }
        0 * _
    }

    def "fails when cache backend throws exception while storing cached result"() {
        def failure = new RuntimeException("store failure")

        when:
        step.execute(work, context)

        then:
        def ex = thrown Exception
        ex.message == "Failed to store cache entry for job ':test'"
        ex.cause == failure

        interaction { withValidCacheKey() }

        then:
        _ * work.allowedToLoadFromCache >> false

        then:
        1 * delegate.execute(work, context) >> delegateResult
        1 * delegateResult.executionResult >> Try.successful(Mock(ExecutionResult))

        then:
        interaction { outputStored { throw failure } }
        0 * _
    }

    def "executes and doesn't store when caching is disabled"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        !result.reused

        _ * context.cachingState >> cachingState
        1 * cachingState.disabledReasons >> ImmutableList.of(new CachingDisabledReason(CachingDisabledReasonCategory.UNKNOWN, "Unknown"))
        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    private void withValidCacheKey() {
        _ * context.cachingState >> cachingState
        1 * cachingState.disabledReasons >> ImmutableList.of()
        1 * cachingState.key >> Optional.of(cacheKey)
    }

    private void outputStored(Closure storeResult) {
        def originMetadata = Mock(OriginMetadata)
        def outputFilesProduceByWork = snapshotsOf("test": [])
        def storeCommand = Mock(BuildCacheStoreCommand)

        1 * delegateResult.outputFilesProduceByWork >> outputFilesProduceByWork
        1 * delegateResult.originMetadata >> originMetadata
        1 * originMetadata.executionTime >> 123L
        1 * buildCacheCommandFactory.createStore(cacheKey, _, outputFilesProduceByWork, 123L) >> storeCommand
        1 * buildCacheController.store(storeCommand) >> { storeResult() }
    }
}
