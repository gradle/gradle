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

package org.gradle.messaging.actor;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.DispatchException;
import org.gradle.messaging.dispatch.MethodInvocation;

/**
 * <p>An {@code Actor} dispatches method calls to a target object in a thread-safe manner. Methods are called either by
 * calling {@link org.gradle.messaging.dispatch.Dispatch#dispatch(Object)} on the actor, or using the proxy object
 * returned by {@link #getProxy(Class)}. Methods are delivered to the target object in the order they are called on the
 * actor, but are delivered to the target object by a single thread at a time. In this way, the target object does not need
 * to perform any synchronisation.</p>
 *
 * <p>An actor uses one of two modes to deliver method calls to the target object:</p>
 *
 * <ul>
 * <li>Non-blocking, or asynchronous, so that method dispatch does not block waiting for the method call to be delivered or executed.
 * In this mode, the method return value or exception is not delivered back to the dispatcher.
 * </li>
 *
 * <li>Blocking, or synchronous, so that method dispatch blocks until the method call has been delivered and executed. In this mode, the
 * method return value or exception is delivered back to the dispatcher.
 * </li>
 *
 * </ul>
 *
 * <p>All implementations of this interface must be thread-safe.</p>
 */
public interface Actor extends Dispatch<MethodInvocation>, Stoppable, ThreadSafe {
    /**
     * Creates a proxy which delivers method calls to the target object.
     *
     * @param type the type for the proxy.
     * @return The proxy.
     */
    <T> T getProxy(Class<T> type);

    /**
     * Stops accepting new method calls, and blocks until all method calls have been executed by the target object.
     *
     * @throws DispatchException When there were any failures dispatching method calls to the target object.
     */
    void stop() throws DispatchException;
}
