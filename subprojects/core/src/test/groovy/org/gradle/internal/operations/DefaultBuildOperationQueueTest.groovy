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

import org.gradle.api.GradleException
import org.gradle.internal.concurrent.DefaultWorkerLimits
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DefaultBuildOperationQueueTest extends Specification {

    public static final String LOG_LOCATION = "<log location>"
    abstract static class TestBuildOperation implements RunnableBuildOperation {
        BuildOperationDescriptor.Builder description() { BuildOperationDescriptor.displayName(toString()) }

        String toString() { getClass().simpleName }
    }

    static class Success extends TestBuildOperation {
        void run(BuildOperationContext buildOperationContext) {
            // do nothing
        }
    }

    static class Failure extends TestBuildOperation {
        void run(BuildOperationContext buildOperationContext) {
            throw new BuildOperationFailure(this, "always fails")
        }
    }

    static class SimpleWorker implements BuildOperationQueue.QueueWorker<TestBuildOperation> {
        void execute(TestBuildOperation run) {
            run.run(null)
        }

        String getDisplayName() {
            return getClass().simpleName
        }
    }

    ResourceLockCoordinationService coordinationService
    BuildOperationQueue operationQueue
    WorkerLeaseService workerRegistry
    WorkerLeaseRegistry.WorkerLeaseCompletion lease

    void setupQueue(int threads) {
        coordinationService = new DefaultResourceLockCoordinationService()
        workerRegistry = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(threads)) {}
        workerRegistry.startProjectExecution(true)
        lease = workerRegistry.startWorker()
        operationQueue = new DefaultBuildOperationQueue(false, workerRegistry, Executors.newFixedThreadPool(threads), new SimpleWorker())
    }

    def "cleanup"() {
        lease?.leaseFinish()
        workerRegistry.stop()
    }

    def "executes all #runs operations in #threads threads"() {
        given:
        setupQueue(threads)
        def success = Mock(TestBuildOperation)

        when:
        runs.times { operationQueue.add(success) }

        and:
        operationQueue.waitForCompletion()

        then:
        runs * success.run(_)

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

    def "does not submit more work processors than #threads threads"() {
        given:
        setupQueue(threads)
        lease.leaseFinish() // Release worker lease to allow operation to run, when there is max 1 worker thread

        def expectedWorkerCount = Math.min(runs, threads)
        def workersStarted = new AtomicInteger()
        def startedLatch = new CountDownLatch(expectedWorkerCount)
        def releaseLatch = new CountDownLatch(1)
        def operationAction = {
            if (workersStarted.get() < expectedWorkerCount) {
                println "started worker in thread ${Thread.currentThread().id} (waiting for ${expectedWorkerCount - workersStarted.incrementAndGet()}).."
            }
        }
        def executor = Mock(Executor)
        def delegateExecutor = Executors.newFixedThreadPool(threads)
        operationQueue = new DefaultBuildOperationQueue(false, workerRegistry, executor, new SimpleWorker())

        println "expecting ${expectedWorkerCount} concurrent work processors to be started..."

        when:
        def waitForCompletionThread = new Thread({
            workerRegistry.runAsWorkerThread {
                runs.times { operationQueue.add(new SynchronizedBuildOperation(operationAction, startedLatch, releaseLatch)) }
                operationQueue.waitForCompletion()
            }
        })
        waitForCompletionThread.start()

        and:
        startedLatch.await(30, TimeUnit.SECONDS)
        releaseLatch.countDown()

        and:
        waitForCompletionThread.join(30000)

        then:
        !waitForCompletionThread.alive
        // The main thread sometimes processes items when there are more items than threads available, so
        // we may only submit workerCount - 1 work processors, but we should never submit more than workerCount
        ((expectedWorkerCount-1)..expectedWorkerCount) * executor.execute(_) >> { args -> delegateExecutor.execute(args[0]) }

        where:
        runs | threads
        1    | 1
        1    | 4
        1    | 10
        5    | 1
        5    | 4
        5    | 10
        20   | 10
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

    def "failures propagate to caller regardless of when it failed #operations with #threads threads"() {
        given:
        setupQueue(threads)
        operations.each { operation ->
            operationQueue.add(operation)
        }
        def failureCount = operations.findAll({ it instanceof Failure }).size()

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

    def "when log location is set value is propagated in exceptions"() {
        given:
        setupQueue(1)
        operationQueue.setLogLocation(LOG_LOCATION)
        operationQueue.add(Stub(TestBuildOperation) {
            run(_) >> { throw new RuntimeException("first") }
        })

        when:
        operationQueue.waitForCompletion()

        then:
        MultipleBuildOperationFailures e = thrown()
        e.message.contains(LOG_LOCATION)
    }

    def "when queue is canceled, unstarted operations do not execute (#runs runs, #threads threads)"() {
        def expectedInvocations = Math.min(runs, threads)
        CountDownLatch startedLatch = new CountDownLatch(expectedInvocations)
        CountDownLatch releaseLatch = new CountDownLatch(1)
        def operationAction = Mock(Runnable)

        given:
        setupQueue(threads)
        lease.leaseFinish() // Release worker lease to allow operation to run, when there is max 1 worker thread

        when:
        def waitForCompletionThread = new Thread({
            workerRegistry.runAsWorkerThread {
                runs.times { operationQueue.add(new SynchronizedBuildOperation(operationAction, startedLatch, releaseLatch)) }
                operationQueue.waitForCompletion()
            }
        })
        waitForCompletionThread.start()

        and:
        // wait for operations to begin running
        startedLatch.await(30, TimeUnit.SECONDS)

        and:
        operationQueue.cancel()

        and:
        // release the running operations to complete
        releaseLatch.countDown()
        waitForCompletionThread.join(30000)

        then:
        !waitForCompletionThread.alive
        expectedInvocations * operationAction.run()

        where:
        runs | threads
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
        void run(BuildOperationContext context) {
            operationAction.run()
            startedLatch.countDown()
            releaseLatch.await()
        }
    }
}
