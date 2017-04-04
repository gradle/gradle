/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.GradleException
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DefaultBuildOperationQueueTest extends Specification {

    public static final String LOG_LOCATION = "<log location>"
    abstract static class TestBuildOperation implements BuildOperation, Runnable {
        public String getDescription() { return toString() }
        public String toString() { return getClass().simpleName }
    }

    static class Success extends TestBuildOperation {
        void run() {
            // do nothing
        }
    }

    static class Failure extends TestBuildOperation {
        void run() {
            throw new BuildOperationFailure(this, "always fails")
        }
    }

    static class SimpleWorker implements BuildOperationWorker<TestBuildOperation> {
        public void execute(TestBuildOperation run) {
            run.run();
        }

        String getDisplayName() {
            return getClass().simpleName
        }
    }

    BuildOperationQueue operationQueue
    WorkerLeaseService workerRegistry

    void setupQueue(int threads) {
        workerRegistry = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), true, threads) {};
        ListeningExecutorService sameThreadExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(threads))
        operationQueue = new DefaultBuildOperationQueue(workerRegistry, sameThreadExecutor, new SimpleWorker())
    }

    def "cleanup"() {
        workerRegistry.stop()
    }

    @Unroll
    def "executes all #runs operations in #threads threads"() {
        given:
        setupQueue(threads)
        def success = Mock(TestBuildOperation)

        when:
        runs.times { operationQueue.add(success) }

        and:
        operationQueue.waitForCompletion()

        then:
        runs * success.run()

        where:
        runs | threads
        0    | 1
        0    | 4
        0    | 10
        1    | 1
        1    | 4
        1    | 10
        5    | 1
        5    | 4
        5    | 10
    }

    def "cannot use operation queue once it has completed"() {
        given:
        setupQueue(1)
        operationQueue.waitForCompletion()

        when:
        operationQueue.add(Mock(TestBuildOperation))

        then:
        thrown IllegalStateException
    }

    @Unroll
    def "failures propagate to caller regardless of when it failed #operations with #threads threads"() {
        given:
        setupQueue(threads)
        operations.each { operation ->
            operationQueue.add(operation)
        }
        def failureCount = operations.findAll({it instanceof Failure}).size()

        when:
        operationQueue.waitForCompletion()

        then:
        // assumes we don't fail early
        MultipleBuildOperationFailures e = thrown()
        e.getCauses().every({ it instanceof GradleException })
        e.getCauses().size() == failureCount

        where:
        [operations, threads] << [
                [[new Success(), new Success(), new Failure()],
                 [new Success(), new Failure(), new Success()],
                 [new Failure(), new Success(), new Success()],
                 [new Failure(), new Failure(), new Failure()],
                 [new Failure(), new Failure(), new Success()],
                 [new Failure(), new Success(), new Failure()],
                 [new Success(), new Failure(), new Failure()]],
                [1, 4, 10]].combinations()
    }

    def "all failures reported in order for a single threaded executor"() {
        given:
        setupQueue(1)
        operationQueue.add(Stub(TestBuildOperation) {
            run() >> { throw new RuntimeException("first") }
        })
        operationQueue.add(Stub(TestBuildOperation) {
            run() >> { throw new RuntimeException("second") }
        })
        operationQueue.add(Stub(TestBuildOperation) {
            run() >> { throw new RuntimeException("third") }
        })

        when:
        operationQueue.waitForCompletion()

        then:
        // assumes we don't fail early
        MultipleBuildOperationFailures e = thrown()
        e.getCauses()*.message == [ 'first', 'second', 'third' ]
    }

    def "when log location is set value is propagated in exceptions"() {
        given:
        setupQueue(1)
        operationQueue.setLogLocation(LOG_LOCATION)
        operationQueue.add(Stub(TestBuildOperation) {
            run() >> { throw new RuntimeException("first") }
        })

        when:
        operationQueue.waitForCompletion()

        then:
        MultipleBuildOperationFailures e = thrown()
        e.message.contains(LOG_LOCATION)
    }

    @Unroll
    def "when queue is canceled, unstarted operations do not execute (#runs runs, #threads threads)" () {
        def expectedInvocations = threads <= runs ? threads : runs
        CountDownLatch startedLatch = new CountDownLatch(expectedInvocations)
        CountDownLatch releaseLatch = new CountDownLatch(1)
        def operationAction = Mock(Runnable)

        given:
        setupQueue(threads)

        when:
        runs.times { operationQueue.add(new SynchronizedBuildOperation(operationAction, startedLatch, releaseLatch)) }
        // wait for operations to begin running
        startedLatch.await()

        and:
        operationQueue.cancel()

        and:
        // release the running operations to complete
        releaseLatch.countDown()
        operationQueue.waitForCompletion()

        then:
        expectedInvocations * operationAction.run()

        where:
        runs | threads
        0    | 1
        0    | 4
        0    | 10
        1    | 1
        1    | 4
        1    | 10
        5    | 1
        5    | 4
        5    | 10
    }

    static class SynchronizedBuildOperation extends TestBuildOperation {
        final Runnable operationAction
        final CountDownLatch startedLatch
        final CountDownLatch releaseLatch

        SynchronizedBuildOperation(Runnable operationAction, CountDownLatch startedLatch, CountDownLatch releaseLatch) {
            this.operationAction = operationAction
            this.startedLatch = startedLatch
            this.releaseLatch = releaseLatch
        }

        @Override
        void run() {
            operationAction.run()
            startedLatch.countDown()
            releaseLatch.await()
        }
    }
}
