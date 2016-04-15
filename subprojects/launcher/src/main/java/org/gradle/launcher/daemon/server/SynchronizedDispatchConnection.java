/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.launcher.daemon.server;

import org.gradle.launcher.daemon.protocol.OutputMessage;
import org.gradle.internal.remote.internal.MessageIOException;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection decorator that synchronizes dispatching.
 *
 * The plan is to replace this with a Connection implementation that queues outgoing messages and dispatches them from a worker thread.
 */
public class SynchronizedDispatchConnection<T> implements RemoteConnection<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronizedDispatchConnection.class);
    private final Object lock = new Object();
    private final RemoteConnection<T> delegate;
    private boolean dispatching;

    public SynchronizedDispatchConnection(RemoteConnection<T> delegate) {
        this.delegate = delegate;
    }

    public void dispatch(final T message) {
        if (!(message instanceof OutputMessage)) {
            LOGGER.debug("thread {}: dispatching {}", Thread.currentThread().getId(), message.getClass());
        }
        synchronized (lock) {
            if (dispatching) {
                // Safety check: dispatching a message should not cause the thread to dispatch another message (eg should not do any logging)
                throw new IllegalStateException("This thread is already dispatching a message.");
            }
            dispatching = true;
            try {
                delegate.dispatch(message);
            } finally {
                dispatching = false;
            }
        }
    }

    @Override
    public void flush() throws MessageIOException {
        synchronized (lock) {
            delegate.flush();
        }
    }

    public T receive() {
        //in case one wants to synchronize this method,
        //bear in mind that it is blocking so it cannot share the same lock as others
        T result = delegate.receive();
        LOGGER.debug("thread {}: received {}", Thread.currentThread().getId(), result == null ? "null" : result.getClass());
        return result;
    }

    public void stop() {
        LOGGER.debug("thread {}: stopping connection", Thread.currentThread().getId());
        delegate.stop();
    }

    public String toString() {
        return delegate.toString();
    }
}
