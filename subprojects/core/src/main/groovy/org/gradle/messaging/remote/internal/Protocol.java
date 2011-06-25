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

/**
 * <p>A protocol implementation. A protocol is a stage in a bi-directional messaging pipeline. It can receive incoming and outgoing messages.
 * In response to these messages, it can dispatch incoming and outgoing messages. It can also register callbacks to be executed later, to allow
 * timeouts and periodic behaviour to be implemented.
 *
 * <p>All methods on protocol are called from a single thread at a time, so implementations do not have to use any locking for their state. The method
 * implementations should not block.
 *
 * @param <T> The message type.
 */
public interface Protocol<T> {
    /**
     * Called to initialise the protocol. The supplied context can be later used to dispatch incoming and outgoing messages.
     *
     * @param context The context.
     */
    void start(ProtocolContext<T> context);

    /**
     * Handles an outgoing message. The context can be used to dispatch incoming and outgoing messages, as required.
     */
    void handleOutgoing(T message);

    /**
     * Handles an incoming message. The context can be used to dispatch incoming and outgoing messages, as required.
     */
    void handleIncoming(T message);

    /**
     * Requests that this protocol initiate its stop messages. The protocol can call {@link ProtocolContext#stopLater()} to defer stop until some
     * messages are received. In which case, it should later call {@link ProtocolContext#stopped()} to indicate it has finished.
     *
     * If the protocol does not call stopLater(), it is assumed to have stopped when this method returns.
     */
    void stopRequested();
}
