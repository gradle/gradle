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
import org.gradle.api.internal.file.TestFiles
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.Execution
import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState
import org.gradle.internal.execution.impl.DefaultOutputSnapshotter
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.execution.workspace.impl.CacheBasedImmutableWorkspaceProvider
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction

import static org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy.FineGrainedCacheEntrySoftDeleter

class AssignImmutableWorkspaceStepConcurrencyTest extends StepSpecBase<IdentityContext> {
    def workspacesRoot = temporaryFolder.file("workspaces").createDir()
    def delegate = new MockStep()

    def deleter = TestFiles.deleter()
    def fileSystemAccess = TestFiles.fileSystemAccess()
    def immutableWorkspaceMetadataStore = Stub(ImmutableWorkspaceMetadataStore) {
        loadWorkspaceMetadata(_ as File) >> Optional.empty()
    }
    def outputSnapshotter = new DefaultOutputSnapshotter(TestFiles.fileCollectionSnapshotter())
    def softDeleter = Stub(FineGrainedCacheEntrySoftDeleter)
    // Don't mock cache since any await() call in withFile {} blocks other mocks
    def cache = new TestInMemoryCacheFactory().openFineGrained(workspacesRoot, "", null)

    def immutableWorkspaceProvider = new StubImmutableWorkspaceProvider()
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

    def work1Started = new CountDownLatch(1)
    def work2Started = new CountDownLatch(1)

    def "only one thread executes work concurrently"() {
        def delegateCalls = new AtomicInteger()
        def thread1Result = new AtomicReference<WorkspaceResult>()
        def thread2Result = new AtomicReference<WorkspaceResult>()
        def step = new AssignImmutableWorkspaceStep(deleter, fileSystemAccess, immutableWorkspaceMetadataStore, outputSnapshotter, delegate)
        Map<Thread, Throwable> exceptions = [:]
        def exceptionHandler = { thread, exception ->
            exceptions.put(thread, exception)
            exception.printStackTrace()
        } as Thread.UncaughtExceptionHandler

        when:
        def thread1 = new Thread({
            thread1Result.set(step.execute(work, context))
        }, "test-thread-1")
        thread1.uncaughtExceptionHandler = exceptionHandler
        delegate.expectCall = { UnitOfWork work, WorkspaceContext context ->
            delegateCalls.incrementAndGet()
            work1Started.countDown()
            work2Started.await()
            Thread.sleep(500)
            return delegateResult
        }
        thread1.start()
        work1Started.await()

        def thread2 = new Thread({
            work1Started.await()
            work2Started.countDown()
            thread2Result.set(step.execute(work, context))
        }, "test-thread-2")
        thread2.uncaughtExceptionHandler = exceptionHandler
        delegate.expectCall = { UnitOfWork work, WorkspaceContext context ->
            delegateCalls.incrementAndGet()
            return delegateResult
        }
        thread2.start()
        thread1.join()
        thread2.join()

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
        BiFunction<UnitOfWork, WorkspaceContext, CachingResult> expectCall

        @Override
        CachingResult execute(UnitOfWork work, WorkspaceContext context) {
            def call = expectCall
            expectCall = null
            return call.apply(work, context)
        }
    }

    private class StubImmutableWorkspaceProvider implements ImmutableWorkspaceProvider {
        final Map<String, CompletableFuture<?>> results = new ConcurrentHashMap<>()

        StubImmutableWorkspaceProvider() {
        }

        @Override
        ImmutableWorkspace getWorkspace(String path) {
            def workspace = new File(workspacesRoot, path)
            return new CacheBasedImmutableWorkspaceProvider.CachedBasedImmutableWorkspace(path, workspace, cache, softDeleter, results)
        }
    }
}
