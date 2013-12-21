/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.remote;

import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;

/**
 * Manages a set of incoming and outgoing channels between 2 peers. Implementations must be thread-safe.
 */
public interface ObjectConnection extends AsyncStoppable {
    /**
     * Creates a transmitter for outgoing messages on the given type. The returned object is thread-safe.
     *
     * @param type The type
     * @return A sink. Method calls made on this object are sent as outgoing messages.
     */
    <T> T addOutgoing(Class<T> type);

    /**
     * Registers a handler for incoming messages on the given type. The provided handler is not required to be
     * thread-safe. Messages are delivered to the handler by a single thread.
     *
     * @param type The type.
     * @param instance The handler instance. Incoming messages on the given type are delivered to this handler.
     */
    <T> void addIncoming(Class<T> type, T instance);

    /**
     * Registers a handler for incoming messages on the given type. The provided handler is not required to be
     * thread-safe. Messages are delivered to the handler by a single thread.
     *
     * @param type The type.
     * @param dispatch The handler instance. Incoming messages on the given type are delivered to this handler.
     */
    void addIncoming(Class<?> type, Dispatch<? super MethodInvocation> dispatch);

    /**
     * Commences a graceful stop of this connection. Stops accepting outgoing messages. Requests that the peer stop
     * sending incoming messages.
     */
    void requestStop();

    /**
     * Performs a graceful stop of this connection. Stops accepting outgoing message. Blocks until all incoming messages
     * have been handled, and all outgoing messages have been handled by the peer.
     */
    void stop();
}
