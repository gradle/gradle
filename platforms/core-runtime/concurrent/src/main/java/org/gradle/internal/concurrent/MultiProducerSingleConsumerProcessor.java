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
import java.util.function.Consumer;

/**
 * Processes values submitted from many producer threads on a single consumer thread.
 * This processor is specifically optimized to avoid as much overhead as possible
 * on the producer-side. This processor is best suited for scenarios like tracing,
 * where it is beneficial to avoid blocking or contention on the thread producing
 * the values to process.
 * <p>
 * Any failure in the processor will terminate the worker thread. Any subsequent
 * interactions with the processor will rethrow the failure.
 * <p>
 * If the worker thread is interrupted during execution, any remaining unprocessed
 * work is dropped.
 */
public class MultiProducerSingleConsumerProcessor<T> {

    /**
     * The number of items to process in a single iteration of the worker loop,
     * before checking failure status or thread interruption.
     */
    public static final int BATCH_SIZE = 1024;

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
        Consumer<T> processor
    ) {
        this.processor = value -> {
            if (failure != null) {
                return;
            }

            try {
                processor.accept(value);
            } catch (Throwable t) {
                failure = t;
            }
        };

        this.queue = new MpscUnboundedArrayQueue<>(1024);
        this.worker = new Thread(this::workerLoop, workerThreadName);
        this.worker.setDaemon(true);
    }

    /**
     * Start the handler. This must be called before any values may be submitted.
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Cannot start processor after it has been started.");
        }
        if (failure != null) {
            throw new IllegalStateException("Cannot restart processor after it failed.", failure);
        }
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
            if (worker.getState() == Thread.State.NEW) {
                throw new IllegalStateException("Cannot submit values before processor has been started.");
            } else {
                throw new IllegalStateException("Cannot submit values after processor has been stopped.");
            }
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
    public void stop(Duration timeout) {
        this.running = false;
        LockSupport.unpark(worker);

        try {
            long timeoutMillis = Math.max(1, timeout.toMillis());
            worker.join(timeoutMillis);
            if (worker.isAlive()) {
                throw new RuntimeException("Timed out waiting for handler to complete.");
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
                    this.failure = new InterruptedException();
                    break;
                }

                // Invoke the processor with at most 1024 new values.
                int processed = queue.drain(processor, 1024);
                if (failure != null) {
                    break;
                }
                if (processed > 0) {
                    continue;
                }

                // Signal that we are going to sleep.
                awake.set(false);

                // Double-check: It is possible that a new value was submitted after we drained the
                // queue but before we set the awake flag to false. This would cause
                // the submitting thread to not attempt to wake up the worker thread.
                // By checking queue.isEmpty() again, we catch values added during the race window.
                if (queue.isEmpty() && running) {
                    LockSupport.park();
                }

                // Most of the time, this will be set to true by the producer thread before unparking
                // the worker. However, `park` may return even if unpark was never requested
                // (spurious wakeup). Therefore, we must always set the `awake` flag to true
                // unconditionally to maintain the invariant that awake=true when the worker is active.
                awake.set(true);
            }
        } catch (Throwable e) {
            // `processor` should never throw, but this catch ensures we do not miss any fatal JVM Errors.
            this.failure = e;
        } finally {
            this.running = false;
            this.queue.clear();
        }
    }

}
