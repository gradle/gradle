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
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.CacheableEntity
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
import org.gradle.internal.file.TreeType

import java.util.function.Consumer
import java.util.function.Function

import static org.gradle.internal.fingerprint.FileCollectionFingerprint.EMPTY

class CacheStepTest extends StepSpec implements FingerprinterFixture {
    def buildCacheController = Mock(BuildCacheController)
    def buildCacheCommandFactory = Mock(BuildCacheCommandFactory)
    def cacheHandler = Mock(CacheHandler)

    def cacheKey = Stub(BuildCacheKey)
    def loadMetadata = Mock(BuildCacheCommandFactory.LoadMetadata)

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
        1 * cacheHandler.load(_) >> { Function<BuildCacheKey, Optional<Try<?>>> loader ->
            loader.apply(cacheKey)
        }

        then:
        1 * buildCacheCommandFactory.createLoad(cacheKey, work) >> loadCommand
        1 * buildCacheController.load(loadCommand) >> Optional.of(loadMetadata)

        then:
        1 * loadMetadata.originMetadata >> cachedOriginMetadata
        1 * loadMetadata.resultingSnapshots >> outputsFromCache
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
        1 * cacheHandler.load(_) >> Try.successful(Optional.empty())

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

    def "executes work non-incrementally and stores after recoverable unpack failure"() {
        def loadCommand = Mock(BuildCacheLoadCommand)
        def loadedOutputFile = file("output.txt")
        def loadedOutputDir = file("output")

        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * buildCacheController.enabled >> true
        1 * context.work >> work
        1 * work.createCacheHandler() >> cacheHandler
        1 * cacheHandler.load(_) >> { Function<BuildCacheKey, Try<Optional<?>>> loader ->
            loader.apply(cacheKey)
        }

        then:
        1 * buildCacheCommandFactory.createLoad(cacheKey, work) >> loadCommand
        1 * buildCacheController.load(loadCommand) >> {
            loadedOutputFile << "output"
            loadedOutputDir.mkdirs()
            loadedOutputDir.file("output.txt") << "output"
            throw new RuntimeException("unpack failure")
        }

        then:
        1 * work.displayName >> "work"
        1 * work.visitOutputTrees(_) >> { CacheableEntity.CacheableTreeVisitor visitor ->
            visitor.visitOutputTree("outputFile", TreeType.FILE, loadedOutputFile)
            visitor.visitOutputTree("outputDir", TreeType.DIRECTORY, loadedOutputDir)
            visitor.visitOutputTree("missingOutputFile", TreeType.FILE, file("missing.txt"))
            visitor.visitOutputTree("missingOutputDir", TreeType.DIRECTORY, file("missing"))
        }

        then:
        loadedOutputFile.assertDoesNotExist()
        loadedOutputDir.assertIsEmptyDir()

        then:
        1 * delegate.execute(_) >> { IncrementalChangesContext delegateContext ->
            assert delegateContext != context
            assert !delegateContext.getChanges().present
            delegateResult
        }
        1 * delegateResult.outcome >> Try.successful(ExecutionOutcome.EXECUTED_NON_INCREMENTALLY)

        then:
        1 * cacheHandler.store(_)
        0 * _
    }

    def "propagates non-recoverable unpack failure"() {
        def unrecoverableUnpackFailure = new RuntimeException("unrecoverable unpack failure")

        when:
        step.execute(context)

        then:
        def ex = thrown Exception
        ex == unrecoverableUnpackFailure

        1 * buildCacheController.enabled >> true
        1 * context.work >> work
        1 * work.createCacheHandler() >> cacheHandler
        1 * cacheHandler.load(_) >> { throw unrecoverableUnpackFailure }
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
        1 * cacheHandler.load(_) >> Try.successful(Optional.empty())

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
        1 * cacheHandler.load(_) >> Try.successful(Optional.empty())

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
}
