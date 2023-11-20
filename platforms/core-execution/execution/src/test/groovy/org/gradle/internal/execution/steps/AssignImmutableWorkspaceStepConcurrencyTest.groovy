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

import org.gradle.api.internal.file.TestFiles
import org.gradle.caching.internal.origin.OriginMetadataFactory
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.impl.DefaultOutputSnapshotter
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.function.BiFunction

class AssignImmutableWorkspaceStepConcurrencyTest extends StepSpecBase<IdentityContext> {
    def workspacesRoot = temporaryFolder.file("workspaces").createDir()
    def immutableWorkspace = workspacesRoot.file("immutable-workspace")
    def delegate = new MockStep()

    def deleter = TestFiles.deleter()
    def fileSystemAccess = TestFiles.fileSystemAccess()
    def originMetadataFactory = Stub(OriginMetadataFactory)
    def outputSnapshotter = new DefaultOutputSnapshotter(TestFiles.fileCollectionSnapshotter())

    def step = new AssignImmutableWorkspaceStep(deleter, fileSystemAccess, originMetadataFactory, outputSnapshotter, delegate)

    def temporaryWorkspace1 = workspacesRoot.file("temporary-workspace-1")
    def temporaryWorkspace2 = workspacesRoot.file("temporary-workspace-2")
    def immutableWorkspaceProvider = new StubImmutableWorkspaceProvider(temporaryWorkspace1, temporaryWorkspace2)
    def work = Stub(ImmutableUnitOfWork) {
        getWorkspaceProvider() >> immutableWorkspaceProvider
    }
    def delegateResult = Stub(CachingResult) {
        getDuration() >> Duration.ofSeconds(1)
        getExecution() >> Try.successful(Stub(ExecutionEngine.Execution))
    }

    def work1Started = new CountDownLatch(1)
    def work1Finished = new CountDownLatch(1)
    def work2Started = new CountDownLatch(1)

    def "handles race condition by returning earlier execution as up-to-date and discarding temporary workspace of the later one"() {
        Map<Thread, Throwable> exceptions = [:]
        def exceptionHandler = { thread, exception ->
            exceptions.put(thread, exception)
            exception.printStackTrace()
        } as Thread.UncaughtExceptionHandler

        when:
        def thread1 = new Thread({
            println "Work 1 started"
            step.execute(work, context)
            println "Work 1 finished"
            work1Finished.countDown()
        }, "test-thread-1")
        thread1.uncaughtExceptionHandler = exceptionHandler
        delegate.expectCall = { UnitOfWork work, WorkspaceContext context ->
            work1Started.countDown()
            work2Started.await()
            new File(context.workspace, "work.txt").text = "work1"
            return delegateResult
        }
        thread1.start()
        work1Started.await()

        then:
        0 * _

        when:
        def thread2 = new Thread({
            println "Work 2 started"
            work1Started.await()
            step.execute(work, context)
            println "Work 2 finished"
        }, "test-thread-2")
        thread2.uncaughtExceptionHandler = exceptionHandler
        delegate.expectCall = { UnitOfWork work, WorkspaceContext context ->
            work2Started.countDown()
            work1Finished.await()
            new File(context.workspace, "work.txt").text = "work2"
            return delegateResult
        }
        thread2.start()

        then:
        0 * _

        when:
        thread1.join()
        thread2.join()

        then:
        exceptions.isEmpty()
        immutableWorkspace.assertIsDir()
        temporaryWorkspace1.assertDoesNotExist()
        temporaryWorkspace2.assertDoesNotExist()
        immutableWorkspace.file("work.txt").text == "work1"
        0 * _
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
        private final List<File> temporaryWorkspaces

        StubImmutableWorkspaceProvider(File... temporaryWorkspaces) {
            this.temporaryWorkspaces = temporaryWorkspaces
        }

        @Override
        ImmutableWorkspace getWorkspace(String path) {
            def temporaryWorkspace = temporaryWorkspaces.pop()
            return new ImmutableWorkspace() {
                @Override
                File getImmutableLocation() {
                    return immutableWorkspace
                }

                @Override
                <T> T withTemporaryWorkspace(ImmutableWorkspace.TemporaryWorkspaceAction<T> action) {
                    temporaryWorkspace.mkdirs()
                    return action.executeInTemporaryWorkspace(temporaryWorkspace)
                }
            }
        }
    }
}
