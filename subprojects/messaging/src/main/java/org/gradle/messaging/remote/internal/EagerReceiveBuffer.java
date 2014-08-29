/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.remote.internal;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.AsyncReceive;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.Receive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Continuously consumes from on or more receivers, serialising to an in memory buffer for synchronous consumption.
 * <p>
 * Messages from the same receive instance are guaranteed to always be returned from {@link #receive()} in sequence. However, no
 * guarantee is made to deliver messages from different sources in chronological order when multiple receive instances
 * are being consumed from.
 * <p>
 * The buffer is bounded, the size of which is specified at construction or defaulting to {@value #DEFAULT_BUFFER_SIZE}.
 * If the buffer fills, the receive threads will block until space becomes available. If a stop is initiated while
 * a thread is waiting for free space in the buffer after having received a message, that message will be discarded.
 * <p>
 * If a stop is initiated while a receive thread is waiting to receive (i.e. is blocked in a {@code receive()} call to the source),
 * the stop will block until this returns. Therefore, it is advised to try to externally stop each of the receive instances being
 * used by the buffer before initiating a stop on the buffer.
 */
public class EagerReceiveBuffer<T> implements Receive<T>, AsyncStoppable {

    private enum State {
        Init, Started, Stopping, Stopped
    }

    private static final int DEFAULT_BUFFER_SIZE = 200;

    private final Lock lock = new ReentrantLock();
    private final Condition notFullOrStop  = lock.newCondition();
    private final Condition notEmptyOrNoReceivers = lock.newCondition();

    private final Collection<Receive<T>> receivers;
    private final CountDownLatch onReceiversExhaustedFinishedLatch = new CountDownLatch(1);

    private final AsyncReceive<T> asyncReceive;
    private final LinkedList<T> queue = new LinkedList<T>();

    private boolean hasActiveReceivers = true;
    private State state = State.Init;

    private static <T> Collection<Receive<T>> toReceiveCollection(Receive<T> receiver) {
        Collection<Receive<T>> list = new ArrayList<Receive<T>>(1);
        list.add(receiver);
        return list;
    }

    public EagerReceiveBuffer(StoppableExecutor executor, Collection<Receive<T>> receivers) {
        this(executor, DEFAULT_BUFFER_SIZE, receivers, null);
    }

    public EagerReceiveBuffer(StoppableExecutor executor, int bufferSize, Receive<T> receiver, Runnable onReceiversExhausted) {
        this(executor, bufferSize, toReceiveCollection(receiver), onReceiversExhausted);
    }

    public EagerReceiveBuffer(StoppableExecutor executor, int bufferSize, Collection<Receive<T>> receivers) {
        this(executor, bufferSize, receivers, null);
    }

    public EagerReceiveBuffer(StoppableExecutor executor, final int bufferSize, Collection<Receive<T>> receivers, final Runnable onReceiversExhausted) {
        if (receivers.size() == 0) {
            throw new IllegalArgumentException("eager receive buffer created with no receivers");
        }

        if (bufferSize < 1) {
            throw new IllegalArgumentException("eager receive buffer size must be positive (value given: " + bufferSize + ")");
        }

        this.receivers = receivers;

        Dispatch<T> dispatch = new Dispatch<T>() {
            public void dispatch(T message) {
                lock.lock();
                try {
                    while (queue.size() == bufferSize && state == State.Started) {
                        try {
                            notFullOrStop.await();
                        } catch (InterruptedException e) {
                            throw UncheckedException.throwAsUncheckedException(e);
                        }
                    }

                    queue.add(message);
                    notEmptyOrNoReceivers.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        };

        this.asyncReceive = new AsyncReceive<T>(executor, dispatch, new Runnable() {
            public void run() {
                lock.lock();
                try {
                    hasActiveReceivers = false;
                    if (onReceiversExhausted != null) {
                        onReceiversExhausted.run();
                    }
                    notEmptyOrNoReceivers.signalAll();
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    lock.unlock();
                    onReceiversExhaustedFinishedLatch.countDown();
                }
            }
        });
    }

    /**
     * Start consuming from the receivers given at construction.
     *
     * @throws IllegalStateException if already started
     */
    public void start() {
        lock.lock();
        try {
            if (state != State.Init) {
                throw new IllegalStateException("this eager receive buffer has already been started");
            }
            state = State.Started;

            for (Receive<T> receiver : receivers) {
                asyncReceive.receiveFrom(receiver);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Receive the next message from the buffer.
     *
     * @return The next message or {@code null} if there are no more messages and no unexhausted receivers.
     */
    public T receive() {
        lock.lock();
        try {
            while (queue.isEmpty() && hasActiveReceivers) {
                try {
                    notEmptyOrNoReceivers.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            if (queue.isEmpty()) {
                // no more messages, and all receivers are exhausted
                assert !hasActiveReceivers;
                return null;
            } else {
                T message = queue.poll();
                assert message != null;
                notFullOrStop.signalAll();
                return message;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops receiving new messages.
     */
    public void requestStop() {
        lock.lock();
        try {
            doRequestStop();
        } finally {
            lock.unlock();
        }
    }

    private void doRequestStop() {
        asyncReceive.requestStop();
        if (hasActiveReceivers) {
            setState(State.Stopping);
        } else {
            setState(State.Stopped);
        }
    }

    private void setState(State state) {
        this.state = state;
        notFullOrStop.signalAll(); // wake up any consumers waiting for space (assume it's a stopish state)
    }

    /**
     * Stops receiving new messages. Blocks until all queued messages have been delivered.
     */
    public void stop() {
        lock.lock();
        try {
            doRequestStop();
        } finally {
            lock.unlock();
        }

        // Have to relinquish lock at this point because the onReceiversExhausted callback that we pass to the async
        // runnable needs to acquire the lock in order to signal the notEmptyOrNoReceivers condition. If we didn't
        // relinquish we would have deadlock. This is harmless due to this method being idempotent.
        try {
            onReceiversExhaustedFinishedLatch.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        lock.lock();
        try {
            asyncReceive.stop();
            setState(State.Stopped);
        } finally {
            lock.unlock();
        }
    }

}