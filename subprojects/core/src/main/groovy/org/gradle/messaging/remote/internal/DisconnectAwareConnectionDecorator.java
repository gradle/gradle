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

import org.gradle.messaging.concurrent.StoppableExecutor;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    private final EagerReceiveBuffer<T> receiveBuffer;
    private Runnable disconnectAction;

    private volatile boolean stopped;

    public DisconnectAwareConnectionDecorator(Connection<T> connection, StoppableExecutor executor) {
        this(connection, executor, DEFAULT_BUFFER_SIZE);
    }

    public DisconnectAwareConnectionDecorator(Connection<T> connection, StoppableExecutor executor, int bufferSize) {
        super(connection);

        receiveBuffer = new EagerReceiveBuffer<T>(executor, bufferSize, connection) {
            protected void onReceiversExhausted() {
                invokeDisconnectActionIfNecessary();
            }
        };

        receiveBuffer.start();
    }

    public Runnable onDisconnect(Runnable disconnectAction) {
        actionLock.lock();
        try {
            Runnable previous = disconnectAction;
            this.disconnectAction = disconnectAction;
            return previous;
        } finally {
            actionLock.unlock();
        }
    }

    public T receive() {
        return receiveBuffer.receive();
    }

    private void invokeDisconnectActionIfNecessary() {
        if (!stopped) {
            actionLock.lock();
            try {
                if (disconnectAction != null) {
                    disconnectAction.run();
                }
            } finally {
                actionLock.unlock();
            }
        }
    }

    public void requestStop() {
        stopped = true;
        super.requestStop();
    }

    public void stop() {
        stopped = true;
        super.stop();
        receiveBuffer.stop();
    }

}