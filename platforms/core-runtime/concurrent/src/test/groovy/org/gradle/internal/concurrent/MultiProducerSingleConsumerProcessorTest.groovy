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

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * Tests {@link MultiProducerSingleConsumerProcessor}
 */
class MultiProducerSingleConsumerProcessorTest extends ConcurrentSpec {

    static final Duration TIMEOUT = Duration.ofSeconds(10)

    def "processes submitted values on worker thread"() {
        given:
        def workerThread = null
        def processed = new CopyOnWriteArrayList<Integer>()
        def processor = newProcessor { Integer value ->
            workerThread = Thread.currentThread()
            processed.add(value)
            instant.processed
        }
        processor.start()

        when:
        processor.submit(123)
        waitFor.processed

        then:
        processed == [123]
        workerThread != Thread.currentThread()
        workerThread.name == "test-worker"

        cleanup:
        stopQuietly(processor)
    }

    def "processes values in order from single producer"() {
        given:
        def processed = new CopyOnWriteArrayList<Integer>()
        def processor = newProcessor { Integer value ->
            processed.add(value)
            if (value == 100) {
                instant.finished
            }
        }
        processor.start()

        when:
        (1..100).each { processor.submit(it) }
        waitFor.finished

        then:
        processed == (1..100).toList()

        cleanup:
        stopQuietly(processor)
    }

    def "interleaves submissions from multiple producers"() {
        given:
        def processed = new CopyOnWriteArrayList<Integer>()
        def processor = newProcessor { Integer value ->
            processed.add(value)
            instant."processed_$value"
        }
        processor.start()

        when:
        async {
            start {
                processor.submit(1)
                instant.producer1Submitted1
                thread.blockUntil.producer2Submitted2
                processor.submit(3)
            }
            start {
                thread.blockUntil.producer1Submitted1
                processor.submit(2)
                instant.producer2Submitted2
            }
        }
        waitFor.processed_3
        processor.stop(TIMEOUT)

        then:
        processed == [1, 2, 3]

        cleanup:
        stopQuietly(processor)
    }

    def "handles multiple concurrent producers"() {
        given:
        def processed = new CopyOnWriteArrayList<int[]>()
        def processor = newProcessor { int[] value -> processed.add(value) }
        processor.start()

        def producerCount = 10
        def itemsPerProducer = 100

        when:
        async {
            producerCount.times { producerId ->
                start {
                    itemsPerProducer.times { itemId ->
                        processor.submit([producerId, itemId] as int[])
                    }
                }
            }
        }
        processor.stop(TIMEOUT)

        then:
        processed.size() == producerCount * itemsPerProducer

        and:
        producerCount.times { producerId ->
            def itemsFromProducer = processed.findAll { it[0] == producerId }.collect { it[1] }
            assert itemsFromProducer == (0..<itemsPerProducer).toList()
        }

        cleanup:
        stopQuietly(processor)
    }

    def "stop drains remaining items before completing"() {
        given:
        def processed = new CopyOnWriteArrayList<Integer>()
        def processor = newProcessor { Integer value ->
            if (value == 1) {
                instant.firstStarted
                thread.blockUntil.canContinue
            }
            processed.add(value)
        }
        processor.start()

        when:
        processor.submit(1)
        waitFor.firstStarted

        processor.submit(2)
        processor.submit(3)

        instant.now("canContinue")
        processor.stop(TIMEOUT)

        then:
        processed == [1, 2, 3]

        cleanup:
        stopQuietly(processor)
    }

    def "propagates failure from processor to subsequent submit"() {
        given:
        def failureInstance = new RuntimeException("Processing failed")
        def processor = newProcessor { Integer value ->
            throw failureInstance
        }
        processor.start()

        when:
        processor.submit(1)

        then:
        def e = waitForFailure { processor.submit(2) }
        e.message == "Cannot submit values after processor has failed."
        e.cause.is(failureInstance)

        cleanup:
        stopQuietly(processor)
    }

    def "throws exception when submitting before start"() {
        given:
        def processor = newProcessor {}

        when:
        processor.submit(1)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot submit values before processor has been started."
    }

    def "throws exception when submitting after stop"() {
        given:
        def processor = newProcessor {}
        processor.start()
        processor.stop(TIMEOUT)

        when:
        processor.submit(1)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot submit values after processor has been stopped."
    }

    def "propagates failure from processor on stop"() {
        given:
        def failureInstance = new RuntimeException("Processing failed")
        def processor = newProcessor { Integer value ->
            throw failureInstance
        }
        processor.start()

        when:
        processor.submit(1)
        processor.stop(TIMEOUT)

        then:
        def e = thrown(RuntimeException)
        e.message == "Failure occurred during processor execution."
        e.cause.is(failureInstance)

        cleanup:
        stopQuietly(processor)
    }

    def "stop times out if worker is blocked"() {
        given:
        def processor = newProcessor { Integer value ->
            instant.started
            thread.block()
        }
        processor.start()

        when:
        processor.submit(1)
        waitFor.started

        processor.stop(Duration.ofMillis(100))

        then:
        def e = thrown(RuntimeException)
        e.message == "Timed out waiting for handler to complete."

        cleanup:
        stopQuietly(processor)
    }

    def "stop with zero timeout waits for at least 1ms"() {
        given:
        def processor = newProcessor { Integer value ->
            instant.started
            thread.block()
        }
        processor.start()

        when:
        processor.submit(1)
        waitFor.started

        processor.stop(Duration.ZERO)

        then:
        def e = thrown(RuntimeException)
        e.message == "Timed out waiting for handler to complete."

        cleanup:
        stopQuietly(processor)
    }

    def "cannot start twice"() {
        given:
        def processor = newProcessor {}
        processor.start()

        when:
        processor.start()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot start processor after it has been started."

        cleanup:
        stopQuietly(processor)
    }

    def "cannot start after failure"() {
        given:
        def failureInstance = new RuntimeException("failed")
        def processor = newProcessor { throw failureInstance }
        processor.start()

        when:
        processor.submit(1)

        then:
        def e = waitForFailure { processor.start() }
        e instanceof IllegalStateException
        e.message == "Cannot restart processor after it failed."
        e.cause.is(failureInstance)

        cleanup:
        stopQuietly(processor)
    }

    def "processes more than batch size"() {
        given:
        def count = MultiProducerSingleConsumerProcessor.BATCH_SIZE * 5
        def processedCount = 0
        def processor = newProcessor { processedCount++ }
        processor.start()

        when:
        count.times { processor.submit(it) }
        processor.stop(TIMEOUT)

        then:
        processedCount == count
    }

    def "handles interruption of worker thread"() {
        given:
        def processor = newProcessor {
            Thread.currentThread().interrupt()
        }
        processor.start()

        when:
        processor.submit(1)

        then:
        def e = waitForFailure { processor.submit(2) }
        e.cause instanceof InterruptedException

        cleanup:
        stopQuietly(processor)
    }

    def "stop is idempotent"() {
        given:
        def processor = newProcessor {}
        processor.start()

        when:
        processor.stop(TIMEOUT)
        processor.stop(TIMEOUT)

        then:
        notThrown(Exception)
    }

    def "interruption during stop results in exception"() {
        given:
        def processor = newProcessor {
            instant.started
            thread.block()
        }
        processor.start()
        processor.submit(1)
        waitFor.started

        when:
        Thread.currentThread().interrupt()
        processor.stop(TIMEOUT)

        then:
        def e = thrown(RuntimeException)
        e.cause instanceof InterruptedException
        Thread.interrupted()

        cleanup:
        stopQuietly(processor)
    }

    /**
     * A failure in the processor may not be immediately visible in the producing thread,
     * so we retry a given action we expect to emit a failure util that failure is emitted.
     */
    private static Throwable waitForFailure(Runnable action) {
        long expiry = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < expiry) {
            try {
                action.run()
            } catch (Throwable t) {
                return t
            }
            Thread.sleep(10)
        }
        throw new RuntimeException("Processor did not fail within 5 seconds")
    }

    private static <T> MultiProducerSingleConsumerProcessor<T> newProcessor(Consumer<T> action) {
        return new MultiProducerSingleConsumerProcessor<T>("test-worker", action)
    }

    private static void stopQuietly(MultiProducerSingleConsumerProcessor<?> processor) {
        try {
            processor.stop(TIMEOUT)
        } catch (Exception ignored) {
        }
    }
}
