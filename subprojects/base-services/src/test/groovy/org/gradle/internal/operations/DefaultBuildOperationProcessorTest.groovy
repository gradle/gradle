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
import org.gradle.internal.concurrent.DefaultExecutorFactory
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch

class DefaultBuildOperationProcessorTest extends Specification {


    public static final String LOG_LOCATION = "<log location>"

    @Unroll
    def "all #operations operations run to completion when using #maxThreads threads"() {
        given:
        def buildOperationProcessor = new DefaultBuildOperationProcessor(new DefaultExecutorFactory(), maxThreads)
        def operation = Mock(DefaultBuildOperationQueueTest.TestBuildOperation)
        def worker = new DefaultBuildOperationQueueTest.SimpleWorker()

        when:
        def queue = buildOperationProcessor.newQueue(worker, LOG_LOCATION)
        operations.times { queue.add(operation) }
        and:
        queue.waitForCompletion()

        then:
        operations * operation.run()

        where:
        // Where operations < maxThreads
        // operations = maxThreads
        // operations >> maxThreads
        operations | maxThreads
        0          | 1
        1          | 1
        20         | 1
        1          | 4
        4          | 4
        20         | 4
    }

    @Unroll
    def "all work run to completion for multiple queues when using multiple threads #maxThreads"() {
        given:
        def amountOfWork = 10
        def worker = new DefaultBuildOperationQueueTest.SimpleWorker()
        def buildOperationProcessor = new DefaultBuildOperationProcessor(new DefaultExecutorFactory(), maxThreads)
        def queues = [
                buildOperationProcessor.newQueue(worker, LOG_LOCATION),
                buildOperationProcessor.newQueue(worker, LOG_LOCATION),
                buildOperationProcessor.newQueue(worker, LOG_LOCATION),
                buildOperationProcessor.newQueue(worker, LOG_LOCATION),
                buildOperationProcessor.newQueue(worker, LOG_LOCATION),
        ]
        def operations = [
                Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
                Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
                Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
                Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
                Mock(DefaultBuildOperationQueueTest.TestBuildOperation),
        ]

        when:
        queues.eachWithIndex { queue, i ->
            amountOfWork.times {
                queue.add(operations[i])
            }
        }

        and:
        queues.each { queue ->
            queue.waitForCompletion()
        }

        then:
        operations.each { operation ->
            amountOfWork * operation.run()
        }

        where:
        maxThreads | _
        1          | _
        4          | _
        10         | _
    }

    def "failures in one queue do not cause failures in other queues"() {
        given:
        def amountOfWork = 10
        def maxThreads = 4
        def buildOperationProcessor = new DefaultBuildOperationProcessor(new DefaultExecutorFactory(), maxThreads)
        def success = Stub(DefaultBuildOperationQueueTest.TestBuildOperation)
        def failure = Stub(DefaultBuildOperationQueueTest.TestBuildOperation) {
            run() >> { throw new Exception() }
        }
        def worker = new DefaultBuildOperationQueueTest.SimpleWorker()
        def successfulQueue = buildOperationProcessor.newQueue(worker, LOG_LOCATION)
        def failedQueue = buildOperationProcessor.newQueue(worker, LOG_LOCATION)

        amountOfWork.times {
            successfulQueue.add(success)
            failedQueue.add(failure)
        }

        when:
        successfulQueue.waitForCompletion()

        then:
        noExceptionThrown()

        when:
        failedQueue.waitForCompletion()

        then:
        thrown MultipleBuildOperationFailures
    }

    def "multiple failures get reported"() {
        given:
        def threadCount = 4
        def buildOperationProcessor = new DefaultBuildOperationProcessor(new DefaultExecutorFactory(), threadCount)
        def worker = new DefaultBuildOperationQueueTest.SimpleWorker()
        def queue = buildOperationProcessor.newQueue(worker, LOG_LOCATION)
        def startLatch = new CountDownLatch(1)
        def operation = Stub(DefaultBuildOperationQueueTest.TestBuildOperation) {
            run() >> {
                startLatch.await()
                throw new GradleException("always fails")
            }
        }
        when:
        threadCount.times { queue.add(operation) }
        startLatch.countDown() // cause all operations to fail
        and:
        queue.waitForCompletion()

        then:
        def e = thrown(MultipleBuildOperationFailures)
        e instanceof MultipleBuildOperationFailures
        ((MultipleBuildOperationFailures) e).getCauses().size() == 4
    }
}
