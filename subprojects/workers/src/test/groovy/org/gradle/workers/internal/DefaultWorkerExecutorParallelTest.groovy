/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import com.google.common.util.concurrent.ListenableFutureTask
import org.gradle.api.internal.file.TestFiles
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.Factory
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.AsyncWorkTracker
import org.gradle.internal.work.ConditionalExecutionQueue
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.UsesNativeServices
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutionException
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Unroll

@UsesNativeServices
class DefaultWorkerExecutorParallelTest extends ConcurrentSpec {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()
    def workerDaemonFactory = Mock(WorkerFactory)
    def workerInProcessFactory = Mock(WorkerFactory)
    def workerNoIsolationFactory = Mock(WorkerFactory)
    def buildOperationWorkerRegistry = Mock(WorkerLeaseRegistry)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def asyncWorkerTracker = Mock(AsyncWorkTracker)
    def forkOptionsFactory = TestFiles.execFactory()
    def workerDirectoryProvider = Stub(WorkerDirectoryProvider) {
        getWorkingDirectory() >> { temporaryFolder.root }
    }
    def executionQueueFactory = Mock(WorkerExecutionQueueFactory)
    def executionQueue = Mock(ConditionalExecutionQueue)
    def classLoaderRegistry = Mock(ClassLoaderRegistry)
    ListenableFutureTask task
    DefaultWorkerExecutor workerExecutor

    def setup() {
        _ * executionQueueFactory.create() >> executionQueue
        workerExecutor = new DefaultWorkerExecutor(workerDaemonFactory, workerInProcessFactory, workerNoIsolationFactory, forkOptionsFactory, buildOperationWorkerRegistry, buildOperationExecutor, asyncWorkerTracker, workerDirectoryProvider, executionQueueFactory, classLoaderRegistry)
    }

    @Unroll
    def "work can be submitted concurrently in IsolationMode.#isolationMode"() {
        when:
        async {
            6.times {
                start {
                    thread.blockUntil.allStarted
                    workerExecutor.submit(TestRunnable.class) { config ->
                        config.isolationMode = isolationMode
                        config.params = []
                    }
                }
            }
            instant.allStarted
        }

        then:
        6 * buildOperationWorkerRegistry.getCurrentWorkerLease()
        6 * executionQueue.submit(_)

        where:
        isolationMode << IsolationMode.values()
    }

    def "can wait on results to complete"() {
        when:
        workerExecutor.await()

        then:
        1 * asyncWorkerTracker.waitForCompletion(_, false)
    }

    def "all errors are thrown when waiting on multiple results"() {
        when:
        workerExecutor.await()

        then:
        1 * asyncWorkerTracker.waitForCompletion(_, false) >> {
            throw new DefaultMultiCauseException(null, new RuntimeException(), new RuntimeException())
        }

        and:
        def e = thrown(WorkerExecutionException)

        and:
        e.causes.size() == 2
    }

    Factory fileFactory() {
        return Stub(Factory) {
            create() >> Stub(File)
        }
    }

    static class TestRunnable implements Runnable {
        @Override
        void run() {
        }
    }
}
