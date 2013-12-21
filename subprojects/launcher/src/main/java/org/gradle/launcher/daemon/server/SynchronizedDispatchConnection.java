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

import org.gradle.internal.concurrent.Synchronizer;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.messaging.remote.internal.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection decorator that synchronizes dispatching.
 */
public class SynchronizedDispatchConnection<T> implements Connection<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronizedDispatchConnection.class);
    
    private final Synchronizer sync = new Synchronizer();
    private final Connection<T> delegate;

    public SynchronizedDispatchConnection(Connection<T> delegate) {
        this.delegate = delegate;
    }
    
    public void requestStop() {
        LOGGER.debug("thread {}: requesting stop for connection", Thread.currentThread().getId());
        delegate.requestStop();
    }

    public void dispatch(final T message) {
        if (!(message instanceof OutputEvent)) {
            LOGGER.debug("thread {}: dispatching {}", Thread.currentThread().getId(), message.getClass());
        }
        sync.synchronize(new Runnable() {
            public void run() {
                delegate.dispatch(message);
            }
        });
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