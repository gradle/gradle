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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import org.gradle.util.UncheckedException;
import org.gradle.messaging.dispatch.Receive;
import org.gradle.messaging.concurrent.AsyncStoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

/**
 * Continuously consumes from on or more receivers, serialising to an in memory buffer for synchronous consumption.
 * <p>
 * Messages from the same receive instance are guaranteed to always be returned from {@link #receive()} in sequence. However, no
 * guarantee is made to deliver messages from different sources in chronological order when multiple multiple receive instances
 * are being consumed from.
 * <p>
 * The buffer is bounded, the size of which is specified at construction or defaulting to {@value DEFAULT_BUFFER_SIZE}.
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

    private static final Logger LOGGER = Logging.getLogger(EagerReceiveBuffer.class);
    private static final int DEFAULT_BUFFER_SIZE = 200;

    final Lock lock = new ReentrantLock();
    final Condition notFullOrStop  = lock.newCondition();
    final Condition notEmptyOrNoReceivers = lock.newCondition();

    private final StoppableExecutor executor;
    private final int bufferSize;
    private final Collection<Receive<T>> receivers;
    private final LinkedList<T> queue = new LinkedList<T>();

    private int numActiveReceivers;
    private State state = State.Init;

    private static <T> Collection<Receive<T>> toReceiveCollection(Receive<T> receiver) {
        Collection<Receive<T>> list = new ArrayList<Receive<T>>(1);
        list.add(receiver);
        return list;
    }
    
    public EagerReceiveBuffer(StoppableExecutor executor, Receive<T> receiver) {
        this(executor, DEFAULT_BUFFER_SIZE, toReceiveCollection(receiver));
    }

    public EagerReceiveBuffer(StoppableExecutor executor, Collection<Receive<T>> receivers) {
        this(executor, DEFAULT_BUFFER_SIZE, receivers);
    }

    public EagerReceiveBuffer(StoppableExecutor executor, int bufferSize, Receive<T> receiver) {
        this(executor, bufferSize, toReceiveCollection(receiver));
    }

    public EagerReceiveBuffer(StoppableExecutor executor, int bufferSize, Collection<Receive<T>> receivers) {
        if (receivers.size() == 0) {
            throw new IllegalArgumentException("eager receive buffer created with no receivers");
        }

        if (bufferSize < 1) {
            throw new IllegalArgumentException("eager receive buffer size must be positive (value given: " + bufferSize + ")");
        }
        
        this.executor = executor;
        this.bufferSize = bufferSize;
        this.receivers = receivers;
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
                receiveFrom(receiver);
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Start consuming from the
     */
    private void receiveFrom(final Receive<? extends T> receiver) {
        onReceiveThreadStart();
        executor.execute(new Runnable() {
            public void run() {
                try {
                    receiveMessages(receiver);
                } finally {
                    onReceiveThreadExit();
                }
            }
        });
    }

    private void onReceiveThreadStart() {
        lock.lock();
        try {
            ++numActiveReceivers;
        } finally {
            lock.unlock();
        }
    }

    private void onReceiveThreadExit() {
        lock.lock();
        try {
            if (--numActiveReceivers == 0) {
                notEmptyOrNoReceivers.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Subclass hook for notification of when all of the receivers are exhausted.
     * <p>
     * Will be invoked on the last spawned receiver thread before it finishes. This means that this
     * is guaranteed to be invoked <b>before</b> the {@link #receive()} method returns {@code null} for the first time.
     */
    protected void onReceiversExhausted() {

    }

    private void receiveMessages(Receive<? extends T> receiver) {
        while (true) {
            T message = null;

            try {
                // NOTE - if stop() is called on this while we are blocked in receive() below, stop() is going to
                //        block until this returns and we exit this method.
                message = receiver.receive();
            } catch (Exception e) {
                // TODO should we propagate this exception somehow?
                LOGGER.error("receiver {} threw exception {}", receiver, e);
                return;
            }

            lock.lock();
            try {
                if (message == null) {
                    if (numActiveReceivers == 1) { // we are the last receiver and are about to finish
                        onReceiversExhausted();
                    }
                    return; // end of this channel so wrap up this consumer
                }

                while (queue.size() == bufferSize && state == State.Started) {
                    try {
                        notFullOrStop.await();
                    } catch (InterruptedException e) {
                        throw UncheckedException.asUncheckedException(e);
                    }
                }

                queue.add(message);
                notEmptyOrNoReceivers.signalAll();

                if (state != State.Started) {
                    return; // stop requested, stop consuming messages
                }
            } finally {
                lock.unlock();
            }
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
            while (queue.isEmpty() && numActiveReceivers > 0) {
                try {
                    notEmptyOrNoReceivers.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.asUncheckedException(e);
                }
            }

            if (queue.isEmpty()) {
                // no more messages, and all receivers are exhausted
                assert numActiveReceivers == 0;
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
        if (numActiveReceivers > 0) {
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

            while (numActiveReceivers > 0) {
                notEmptyOrNoReceivers.await();
            }

            executor.stop();
            setState(State.Stopped);
        } catch (InterruptedException e) {
            throw UncheckedException.asUncheckedException(e);
        } finally {
            lock.unlock();
        }
    }

}