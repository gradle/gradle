/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy
import org.gradle.cache.FineGrainedPersistentCache
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.Execution
import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState
import org.gradle.internal.execution.workspace.impl.CacheBasedImmutableWorkspaceProvider
import org.gradle.internal.file.Deleter
import org.gradle.internal.file.FileType
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction

import static org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy.FineGrainedCacheEntrySoftDeleter

class AssignImmutableWorkspaceStepConcurrencyTest extends StepSpecBase<IdentityContext> {

    def workspacesRoot = temporaryFolder.file("workspaces").createDir()
    def deleter = Stub(Deleter)
    def filesystemLocation = Stub(FileSystemLocationSnapshot) {
        getType() >> FileType.Missing
    }
    def fileSystemAccess = Stub(FileSystemAccess) {
        read(_ as String) >> filesystemLocation
    }
    def immutableWorkspaceMetadataStore = Stub(ImmutableWorkspaceMetadataStore) {
        loadWorkspaceMetadata(_ as File) >> Optional.empty()
    }
    def outputSnapshotter = Stub(OutputSnapshotter)
    def softDeleter = Stub(FineGrainedCacheEntrySoftDeleter)
    // Don't mock cache since any await() call in withFile {} blocks other mocks
    def cache = new TestInMemoryCacheFactory().openFineGrained(workspacesRoot, "", null)
    def cleanupStrategy = Stub(FineGrainedMarkAndSweepCacheCleanupStrategy) {
        getSoftDeleter(_ as FineGrainedPersistentCache) >> softDeleter
    }
    def immutableWorkspaceProvider = new CacheBasedImmutableWorkspaceProvider(
        Stub(SingleDepthFileAccessTracker),
        cache,
        cleanupStrategy
    )
    def work = Stub(ImmutableUnitOfWork) {
        getWorkspaceProvider() >> immutableWorkspaceProvider
    }
    def originMetadata = Stub(OriginMetadata)
    def delegateResult = Stub(CachingResult) {
        getDuration() >> Duration.ofSeconds(1)
        getExecution() >> Try.successful(Stub(Execution) {
            getOutcome() >> Execution.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
        })
        getAfterExecutionOutputState() >> Optional.of(new DefaultExecutionOutputState(true, ImmutableSortedMap.of(), originMetadata, false))
    }

    Map<Thread, Throwable> exceptions = [:]
    Thread.UncaughtExceptionHandler exceptionHandler = { thread, exception ->
        exceptions.put(thread, exception)
        exception.printStackTrace()
    } as Thread.UncaughtExceptionHandler

    def work1Started = new CountDownLatch(1)
    def work2Started = new CountDownLatch(1)

    def "only one thread executes work concurrently"() {
        def delegateCalls = new AtomicInteger()
        def thread1Result = new AtomicReference<WorkspaceResult>()
        def delegate1 = new MockStep({ UnitOfWork work, WorkspaceContext context ->

            delegateCalls.incrementAndGet()
            work1Started.countDown()
            work2Started.await()
            Thread.sleep(1500)
            return delegateResult
        })
        def step1 = new AssignImmutableWorkspaceStep(deleter, fileSystemAccess, immutableWorkspaceMetadataStore, outputSnapshotter, delegate1)
        def thread1 = new Thread({
            thread1Result.set(step1.execute(work, context))
        }, "test-thread-1")
        thread1.uncaughtExceptionHandler = exceptionHandler

        def thread2Result = new AtomicReference<WorkspaceResult>()
        def delegate2 = new MockStep( { UnitOfWork work, WorkspaceContext context ->
            delegateCalls.incrementAndGet()
            return delegateResult
        })
        def step2 = new AssignImmutableWorkspaceStep(deleter, fileSystemAccess, immutableWorkspaceMetadataStore, outputSnapshotter, delegate2)
        def thread2 = new Thread({
            work1Started.await()
            work2Started.countDown()
            thread2Result.set(step2.execute(work, context))
        }, "test-thread-2")
        thread2.uncaughtExceptionHandler = exceptionHandler

        when:
        thread1.start()
        thread2.start()
        thread1.join(20000)
        thread2.join(20000)

        then:
        !thread1.isAlive()
        !thread2.isAlive()
        exceptions.isEmpty()

        and:
        delegateCalls.get() == 1
        thread1Result.get().execution.get().outcome == Execution.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
        thread2Result.get().execution.get().outcome == Execution.ExecutionOutcome.UP_TO_DATE
    }

    // We need custom mock classes because Spock does not support multi-threaded mocks
    private static class MockStep implements Step<WorkspaceContext, CachingResult> {
        final BiFunction<UnitOfWork, WorkspaceContext, CachingResult> expectCall

        MockStep(BiFunction<UnitOfWork, WorkspaceContext, CachingResult> expectCall) {
            this.expectCall = expectCall
        }

        @Override
        CachingResult execute(UnitOfWork work, WorkspaceContext context) {
            return expectCall.apply(work, context)
        }
    }
}
