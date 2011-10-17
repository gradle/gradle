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
import org.gradle.messaging.concurrent.StoppableExecutor;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CountDownLatch;

/**
 * A {@link DisconnectAwareConnection} implementation that decorates an existing connection, adding disconnect awareness.
 * <p>
 * This implementation uses {@link EagerReceiveBuffer} internally to receive messages as fast as they are sent.
 * The messages are then collected by {@link #receive()} as per normal.
 * <p>
 * NOTE: due to the use of a bounded buffer, disconnection may not be detected immediately if the internal buffer is full.
 */
public class DisconnectAwareConnectionDecorator<T> extends DelegatingConnection<T> implements DisconnectAwareConnection<T> {

    private static final Logger LOGGER = Logging.getLogger(DisconnectAwareConnectionDecorator.class);
    private static final int DEFAULT_BUFFER_SIZE = 200;

    private final Lock actionLock = new ReentrantLock();
    private final CountDownLatch actionSetLatch = new CountDownLatch(1);
    private final EagerReceiveBuffer<T> receiveBuffer;
    private Runnable disconnectAction;

    private volatile boolean stopped;

    public DisconnectAwareConnectionDecorator(Connection<T> connection, StoppableExecutor executor) {
        this(connection, executor, DEFAULT_BUFFER_SIZE);
    }

    public DisconnectAwareConnectionDecorator(Connection<T> connection, StoppableExecutor executor, int bufferSize) {
        super(connection);

        // EagerReceiveBuffer guaranteest that onReceiversExhausted() will be completed before it returns null from receive(),
        // which means we satisfy the condition of the DisconnectAwareConnection contract that disconnect handlers must complete
        // before receive() returns null.

        receiveBuffer = new EagerReceiveBuffer<T>(executor, bufferSize, connection, new Runnable() {
            public void run() {
                invokeDisconnectAction();
            }
        });

        receiveBuffer.start();
    }

    public Runnable onDisconnect(Runnable disconnectAction) {
        actionLock.lock();
        try {
            Runnable previous = disconnectAction;
            this.disconnectAction = disconnectAction;
            actionSetLatch.countDown();
            return previous;
        } finally {
            actionLock.unlock();
        }
    }

    public T receive() {
        return receiveBuffer.receive();
    }

    private void invokeDisconnectAction() {
        if (!stopped) {
            try {
                actionSetLatch.await();
            } catch (InterruptedException e) {
                throw UncheckedException.asUncheckedException(e);
            }

            actionLock.lock();
            try {
                if (disconnectAction != null) {
                    LOGGER.debug("about to invoke disconnection handler {}", disconnectAction);
                    try {
                        disconnectAction.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                        LOGGER.error("disconnection handler threw exception", e);
                        throw UncheckedException.asUncheckedException(e);
                    }
                    LOGGER.info("completed disconnection handler {}", disconnectAction);
                }
            } finally {
                actionLock.unlock();
            }
        }
    }

    public void requestStop() {
        stopped = true;
        onDisconnect(null);
        super.requestStop();
    }

    public void stop() {
        stopped = true;
        onDisconnect(null);
        super.stop();
        receiveBuffer.stop();
    }

}