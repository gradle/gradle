/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.remote;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.AsyncStoppable;

/**
 * Manages a set of incoming and outgoing channels between 2 peers.
 *
 * NOTE: This contract guarantees only partial thread-safety. Configuration and {@link #connect()} are not thread-safe and must be performed by the same thread,
 * generally some configuration thread. Only the stop methods are thread-safe. The other methods will be made thread-safe (or moved somewhere else) later.
 */
public interface ObjectConnection extends AsyncStoppable, ObjectConnectionBuilder {
    /**
     * Completes the connection. No further configuration can be done.
     */
    void connect();

    /**
     * Commences a graceful stop of this connection. Stops accepting outgoing messages. Requests that the peer stop
     * sending incoming messages.
     */
    @Override
    void requestStop();

    /**
     * Performs a graceful stop of this connection. Stops accepting outgoing messages. Blocks until all incoming messages
     * have been handled, and all outgoing messages have been forwarded to the peer.
     */
    @Override
    void stop();

    /**
     * Indicate that the execution containing this {@code ObjectConnection} has been prematurely stopped.
     */
    void abort();

    /**
     * Add a callback upon unrecoverable errors, e.g. broken connection. Should not throw any exceptions because
     * this is the last line of defense.
     * @param handler the callback
     */
    void addUnrecoverableErrorHandler(Action<Throwable> handler);
}
