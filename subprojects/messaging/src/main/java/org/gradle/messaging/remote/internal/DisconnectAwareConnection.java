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
 * A {@code DisconnectAwareConnection} is a connection capable of executing an action when
 * the other side of connection disconnects before the stop method is called on this connection.
 * <p>
 * Implementations must guarantee that the disconnect action completes before {@code null} is returned from
 * the {@link #receive()} method. Furthermore, if a disconnect action has not yet been set the {@code receive()} method
 * MUST NOT return {@code null} until a disconnection action is set and executed. This means that if {@link #labelonDisconnect(Runnable)} is
 * never called on a {@code DisconnectAwareConnection}, it's {@code receive()} method will never return null as it will block indefinitely.
 */
public interface DisconnectAwareConnection<T> extends Connection<T> {

    /**
     * Used to specify the action to take when a disconnection is detected.
     * <p>
     * It is guaranteed that calling {@code receive()} on this connection will forever return {@code null} after
     * the disconnect action has been started.
     * <p>
     * If this connection has an associates disconnect action at the time a disconnection is detected, it is guaranteed
     * to be invoked <b>before</b> any call to {@code receive()} will return null.
     * <p>
     * If the {@code stop()} method is called on this connection before a disconnection is detected, the disconnect action
     * will never be called.
     * 
     * @param disconnectAction The action to perform on disconnection, or {@code null} to remove any existing action.
     * @return The previous disconnect action, or {@code null} if no action had been previously registered.
     */
    Runnable onDisconnect(Runnable disconnectAction);

}