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
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.command.BuildCacheCommandFactory
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.controller.BuildCacheLoadCommand
import org.gradle.caching.internal.controller.BuildCacheStoreCommand
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.CacheHandler
import org.gradle.internal.execution.CurrentSnapshotResult
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.IncrementalChangesContext
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.internal.file.TreeType
import spock.lang.Shared
import spock.lang.Unroll

import java.util.function.Consumer
import java.util.function.Function

import static org.gradle.internal.fingerprint.FileCollectionFingerprint.EMPTY

class CacheStepTest extends StepSpec implements FingerprinterFixture {
    def buildCacheController = Mock(BuildCacheController)
    def buildCacheCommandFactory = Mock(BuildCacheCommandFactory)
    def cacheHandler = Mock(CacheHandler)

    def cacheKey = Stub(BuildCacheKey)
    def loadMetadata = Mock(BuildCacheCommandFactory.LoadMetadata)
    @Shared def rebuildChanges = Mock(ExecutionStateChanges)
    def localStateFile = file("local-state.txt") << "local state"

    def step = new CacheStep(buildCacheController, buildCacheCommandFactory, delegate)
    def delegateResult = Mock(CurrentSnapshotResult)
    def context = Mock(IncrementalChangesContext)

    def "loads from cache"() {
        def cachedOriginMetadata = Mock(OriginMetadata)
        def outputsFromCache = fingerprintsOf("test": [])
        def loadCommand = Mock(BuildCacheLoadCommand)

        when:
        def result = step.execute(context)

        then:
        result.outcome.get() == ExecutionOutcome.FROM_CACHE
        result.reused
        result.originMetadata == cachedOriginMetadata
        result.finalOutputs == outputsFromCache

        1 * buildCacheController.enabled >> true
        1 * context.work >> work
        1 * work.createCacheHandler() >> cacheHandler
        1 * cacheHandler.load(_) >> { Function<BuildCacheKey, Optional<?>> loader ->
            loader.apply(cacheKey)
        }

        then:
        1 * buildCacheCommandFactory.createLoad(cacheKey, work) >> loadCommand
        1 * buildCacheController.load(loadCommand) >> Optional.of(loadMetadata)

        then:
        1 * loadMetadata.originMetadata >> cachedOriginMetadata
        1 * loadMetadata.resultingSnapshots >> outputsFromCache
        interaction { localStateIsRemoved() }
        0 * _
    }

    def "executes work and stores in cache on cache miss"() {
        def storeCommand = Mock(BuildCacheStoreCommand)
        def finalOutputs = ImmutableSortedMap.of("test", EMPTY)
        def originMetadata = Mock(OriginMetadata)

        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * buildCacheController.enabled >> true
        1 * context.work >> work
        1 * work.createCacheHandler() >> cacheHandler
        1 * cacheHandler.load(_) >> Optional.empty()

        then:
        1 * delegate.execute(context) >> delegateResult
        1 * delegateResult.outcome >> Try.successful(ExecutionOutcome.EXECUTED_NON_INCREMENTALLY)

        then:
        1 * cacheHandler.store(_) >> { Consumer<BuildCacheKey> storer -> storer.accept(cacheKey) }

        then:
        1 * context.work >> work
        1 * delegateResult.finalOutputs >> finalOutputs
        1 * delegateResult.originMetadata >> originMetadata
        1 * originMetadata.executionTime >> 123L
        1 * buildCacheCommandFactory.createStore(cacheKey, work, finalOutputs, 123L) >> storeCommand
        1 * buildCacheController.store(storeCommand)
        0 * _
    }

    @Unroll
    def "executes work #description non-incrementally and stores after unpack failure"() {
        def loadedOutputFile = file("output.txt")
        def loadedOutputDir = file("output")

        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * buildCacheController.enabled >> true
        1 * context.work >> work
        1 * work.createCacheHandler() >> cacheHandler
        1 * cacheHandler.load(_) >> { Function<BuildCacheKey, Optional<?>> loader ->
            loadedOutputFile << "output"
            loadedOutputDir.mkdirs()
            loadedOutputDir.file("output.txt") << "output"
            throw new RuntimeException("unpack failure")
        }

        then:
        1 * work.displayName >> "work"
        1 * work.visitOutputProperties(_) >> { UnitOfWork.OutputPropertyVisitor visitor ->
            visitor.visitOutputProperty("outputFile", TreeType.FILE, ImmutableFileCollection.of(loadedOutputFile))
            visitor.visitOutputProperty("outputDir", TreeType.DIRECTORY, ImmutableFileCollection.of(loadedOutputDir))
            visitor.visitOutputProperty("missingOutputFile", TreeType.FILE, ImmutableFileCollection.of(file("missing.txt")))
            visitor.visitOutputProperty("missingOutputDir", TreeType.DIRECTORY, ImmutableFileCollection.of(file("missing")))
        }
        loadedOutputFile.assertDoesNotExist()
        loadedOutputDir.assertIsEmptyDir()
        interaction { localStateIsRemoved() }

        then:
        1 * context.changes >> Optional.ofNullable(changes)
        1 * delegate.execute(_) >> { IncrementalChangesContext delegateContext ->
            assert delegateContext != context
            check(delegateContext)
            delegateResult
        }
        1 * delegateResult.outcome >> Try.successful(ExecutionOutcome.EXECUTED_NON_INCREMENTALLY)

        then:
        1 * cacheHandler.store(_)
        0 * _

        where:
        description       | check                                                                                        | changes
        "without changes" | { IncrementalChangesContext context -> assert !context.getChanges().present }                | null
        "with changes"    | { IncrementalChangesContext context -> assert context.getChanges().get() == rebuildChanges } | Mock(ExecutionStateChanges) {
            1 * withEnforcedRebuild("Outputs removed due to failed load from cache") >> rebuildChanges
        }
    }

    def "propagates non-recoverable unpack failure"() {
        when:
        step.execute(context)

        then:
        def ex = thrown Exception
        ex.message == "cleanup failure"

        1 * buildCacheController.enabled >> true
        1 * context.work >> work
        1 * work.createCacheHandler() >> cacheHandler
        1 * cacheHandler.load(_) >> { throw new RuntimeException("unpack failure") }

        then:
        1 * work.displayName >> "work"
        1 * work.visitOutputProperties(_) >> {
            throw new RuntimeException("cleanup failure")
        }
        interaction { localStateIsRemoved() }
        0 * _
    }

    def "does not store result of failed execution in cache"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        !result.reused

        1 * buildCacheController.enabled >> true
        1 * context.work >> work
        1 * work.createCacheHandler() >> cacheHandler
        1 * cacheHandler.load(_) >> Optional.empty()

        then:
        1 * delegate.execute(context) >> delegateResult
        1 * delegateResult.outcome >> Try.failure(new RuntimeException("failure"))

        then:
        1 * context.work >> work
        1 * work.displayName >> "Display name"
        0 * cacheHandler.store(_)
        0 * _
    }

    def "does not fail when cache backend throws exception while storing cached result"() {
        def storeCommand = Mock(BuildCacheStoreCommand)
        def finalOutputs = ImmutableSortedMap.of("test", EMPTY)
        def originMetadata = Mock(OriginMetadata)

        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * buildCacheController.enabled >> true
        1 * context.work >> work
        1 * work.createCacheHandler() >> cacheHandler
        1 * cacheHandler.load(_) >> Optional.empty()

        then:
        1 * delegate.execute(context) >> delegateResult
        1 * delegateResult.outcome >> Try.successful(ExecutionOutcome.EXECUTED_NON_INCREMENTALLY)

        then:
        1 * cacheHandler.store(_) >> { Consumer<BuildCacheKey> storer -> storer.accept(cacheKey) }

        then:
        1 * context.work >> work
        1 * delegateResult.finalOutputs >> finalOutputs
        1 * delegateResult.originMetadata >> originMetadata
        1 * originMetadata.executionTime >> 123L
        1 * buildCacheCommandFactory.createStore(cacheKey, work, finalOutputs, 123L) >> storeCommand
        1 * buildCacheController.store(storeCommand) >> { throw new RuntimeException("store failure") }
        0 * _
    }

    def "executes and doesn't store when caching is disabled"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        !result.reused

        1 * buildCacheController.enabled >> false
        1 * delegate.execute(_) >> delegateResult
        0 * _
    }

    private void localStateIsRemoved() {
        1 * work.visitLocalState(_) >> { UnitOfWork.LocalStateVisitor visitor ->
            visitor.visitLocalStateRoot(localStateFile)
        }
        !localStateFile.exists()
    }
}
