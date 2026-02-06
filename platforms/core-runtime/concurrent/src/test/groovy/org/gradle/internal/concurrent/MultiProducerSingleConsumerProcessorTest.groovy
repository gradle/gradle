/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.concurrent

import org.jctools.queues.MessagePassingQueue
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

@Timeout(60)
class MultiProducerSingleConsumerProcessorTest extends Specification {

    static final Duration STOP_TIMEOUT = Duration.ofSeconds(5)

    def "processes submitted values on worker thread"() {
        given:
        def processed = new CopyOnWriteArrayList<String>()
        def processor = newProcessor { String value -> processed.add(value) }
        processor.start()

        when:
        processor.submit("one")
        processor.submit("two")
        processor.submit("three")
        processor.stop(STOP_TIMEOUT)

        then:
        processed.containsAll(["one", "two", "three"])
        processed.size() == 3

        cleanup:
        stopQuietly(processor)
    }

    def "processes values in order from single producer"() {
        given:
        def processed = new CopyOnWriteArrayList<Integer>()
        def processor = newProcessor { Integer value -> processed.add(value) }
        processor.start()

        when:
        (1..100).each { processor.submit(it) }
        processor.stop(STOP_TIMEOUT)

        then:
        processed == (1..100).toList()

        cleanup:
        stopQuietly(processor)
    }

    def "handles multiple concurrent producers"() {
        given:
        def processed = new CopyOnWriteArrayList<String>()
        def processor = newProcessor { String value -> processed.add(value) }
        processor.start()

        def producerCount = 10
        def itemsPerProducer = 100
        def startLatch = new CountDownLatch(1)
        def doneLatch = new CountDownLatch(producerCount)

        when:
        producerCount.times { producerId ->
            Thread.start {
                startLatch.await()
                itemsPerProducer.times { itemId ->
                    processor.submit("producer-${producerId}-item-${itemId}" as String)
                }
                doneLatch.countDown()
            }
        }
        startLatch.countDown()
        doneLatch.await()
        processor.stop(Duration.ofSeconds(10))

        then:
        processed.size() == producerCount * itemsPerProducer

        and: "items from each individual producer appear in their original submission order"
        producerCount.times { producerId ->
            def prefix = "producer-${producerId}-item-"
            def itemsFromProducer = processed.findAll { it.startsWith(prefix) }
                .collect { Integer.parseInt(it.substring(prefix.length())) }
            assert itemsFromProducer == (0..<itemsPerProducer).toList()
        }

        cleanup:
        stopQuietly(processor)
    }

    def "stop drains remaining items before completing"() {
        given:
        def processed = new CopyOnWriteArrayList<String>()
        def processingStarted = new CountDownLatch(1)
        def continueProcessing = new CountDownLatch(1)
        def processor = newProcessor { String value ->
            if (value == "first") {
                processingStarted.countDown()
                continueProcessing.await()
            }
            processed.add(value)
        }
        processor.start()

        when:
        processor.submit("first")
        processingStarted.await()
        // Submit more items while first is being processed
        processor.submit("second")
        processor.submit("third")
        // Allow processing to continue
        continueProcessing.countDown()
        processor.stop(STOP_TIMEOUT)

        then:
        processed.containsAll(["first", "second", "third"])
        processed.size() == 3

        cleanup:
        continueProcessing.countDown()
        stopQuietly(processor)
    }

    def "propagates failure from processor to subsequent submit"() {
        given:
        def failure = new RuntimeException("Processing failed")
        def processingStarted = new CountDownLatch(1)
        def processor = newProcessor { String value ->
            processingStarted.countDown()
            throw failure
        }
        processor.start()

        when:
        processor.submit("trigger-failure")
        processingStarted.await()
        // Give the worker time to fail
        Thread.sleep(100)
        processor.submit("should-fail")

        then:
        def e = thrown(RuntimeException)
        e == failure

        cleanup:
        stopQuietly(processor)
    }

    def "throws exception when submitting before start"() {
        given:
        def processor = newProcessor {}

        when:
        processor.submit("too-early")

        then:
        def e = thrown(IllegalStateException)
        // TODO: better error message
        e.message.contains("stopped")
    }

    def "throws exception when submitting after stop"() {
        given:
        def processor = newProcessor {}
        processor.start()
        processor.stop(STOP_TIMEOUT)

        when:
        processor.submit("too-late")

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("stopped")
    }

    def "propagates failure from processor on stop"() {
        given:
        def failure = new RuntimeException("Processing failed")
        def processor = newProcessor { String value ->
            throw failure
        }
        processor.start()

        when:
        processor.submit("trigger-failure")
        processor.stop(STOP_TIMEOUT)

        then:
        def e = thrown(RuntimeException)
        e == failure

        cleanup:
        stopQuietly(processor)
    }

    def "stop times out if worker is blocked"() {
        given:
        def processingStarted = new CountDownLatch(1)
        def blockForever = new CountDownLatch(1)
        def processor = newProcessor { String value ->
            processingStarted.countDown()
            blockForever.await()
        }
        processor.start()

        when:
        processor.submit("block")
        processingStarted.await()
        processor.stop(Duration.ofMillis(100))

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Timed out")

        cleanup:
        blockForever.countDown()
        stopQuietly(processor)
    }

    private static <T> MultiProducerSingleConsumerProcessor<T> newProcessor(
        @DelegatesTo(value = MessagePassingQueue.Consumer) Closure action
    ) {
        return new MultiProducerSingleConsumerProcessor<T>("test-worker", action as MessagePassingQueue.Consumer<T>)
    }

    private static void stopQuietly(MultiProducerSingleConsumerProcessor<?> processor) {
        try {
            processor.stop(STOP_TIMEOUT)
        } catch (Exception ignored) {
        }
    }
}
