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

package org.gradle.internal.concurrent;


import org.gradle.internal.UncheckedException;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Processes values submitted from many producer threads on a single consumer thread.
 * This processor is specifically optimized to avoid as much overhead as possible
 * on the producer-side. This processor is best suited for scenarios like tracing,
 * where it is beneficial to avoid blocking or contention on the thread producing
 * the values to process.
 * <p>
 * Any failure in the processor will terminate the worker thread. Any subsequent
 * calls to {@link #submit(Object)} will throw an exception.
 * <p>
 * If the worker thread is interrupted during execution, any remaining unprocessed
 * work is dropped.
 */
public class MultiProducerSingleConsumerProcessor<T> {

    /**
     * Processes submitted values on a separate thread.
     */
    private final MessagePassingQueue.Consumer<T> processor;

    /**
     * Queue containing unprocessed submitted values.
     */
    private final MessagePassingQueue<T> queue;

    /**
     * The worker thread that processes submitted values.
     */
    private final Thread worker;

    /**
     * Non-null if an exception occurred in the worker loop, causing
     * the worker thread to terminate. Any attempts to submit or stop the
     * worker will throw this failure, if present.
     */
    private volatile @Nullable Throwable failure;

    /**
     * True if the handler has not been stopped.
     */
    private volatile boolean running = false;

    /**
     * True if the worker thread is actively handling values. False if the
     * submitting thread must unpark the worker thread.
     */
    private final AtomicBoolean awake = new AtomicBoolean(true);

    public MultiProducerSingleConsumerProcessor(
        String workerThreadName,
        MessagePassingQueue.Consumer<T> processor
    ) {
        this.processor = processor;

        this.queue = new MpscUnboundedArrayQueue<>(1024);
        this.worker = new Thread(this::workerLoop, workerThreadName);
        this.worker.setDaemon(true);
    }

    /**
     * Start the handler. This must be called before any values may be submitted.
     */
    public void start() {
        this.running = true;
        this.worker.start();
    }

    /**
     * Submit a value to be processed. This method returns immediately, and
     * the value will be passed to the processor on a separate thread.
     */
    public void submit(T value) {
        if (failure != null) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }

        if (!running) {
            throw new IllegalStateException("Cannot submit values after processor has been stopped.");
        }

        if (!queue.offer(value)) {
            throw new IllegalStateException("Failed to offer value to queue");
        }

        if (awake.get()) {
            return;
        }

        if (!awake.getAndSet(true)) {
            // Only pay the cost to notify the worker if it is not already
            // processing values.
            LockSupport.unpark(worker);
        }
    }

    /**
     * Stop the handler, waiting for the given timeout. An exception
     * is thrown if the worker did not complete by the timeout.
     */
    public void stop(@Nullable Duration timeout) {
        this.running = false;
        LockSupport.unpark(worker);

        try {
            worker.join(timeout == null ? 0 : timeout.toMillis());
            if (worker.isAlive()) {
                throw new RuntimeException("Timed out waiting for handler to complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (failure != null) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }
    }

    private void workerLoop() {
        try {
            while (running || !queue.isEmpty()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                int processed = queue.drain(processor, 1024);
                if (processed > 0) {
                    continue;
                }

                // Signal that we are going to sleep.
                awake.set(false);

                // It is possible that a new value was submitted after we drained the
                // queue but before we set the awake flag to false. This would cause
                // the submitting thread to not attempt to wake up the worker thread.
                if (queue.isEmpty() && running) {
                    LockSupport.park();
                }

                // Most of the time, this will be set to true by the producer thread before unparking
                // the worker. However, `park` may return even if unpark was never requested.
                // Therefore, We must always set the `awake` flag to true unconditionally.
                awake.set(true);
            }
        } catch (Throwable e) {
            this.failure = e;
        } finally {
            this.running = false;
            this.queue.clear();
        }
    }

}
