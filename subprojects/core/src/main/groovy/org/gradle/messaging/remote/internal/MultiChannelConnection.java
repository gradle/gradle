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

package org.gradle.messaging.remote.internal;

import org.gradle.messaging.concurrent.AsyncStoppable;
import org.gradle.messaging.dispatch.Addressable;
import org.gradle.messaging.dispatch.Dispatch;

public interface MultiChannelConnection<T> extends Addressable, AsyncStoppable {
    /**
     * Adds a destination for outgoing messages on the given channel. The returned value is thread-safe.
     */
    Dispatch<T> addOutgoingChannel(Object channelKey);

    /**
     * Adds a handler for incoming messages on the given channel. The given dispatch is only ever used by a single
     * thread at any given time.
     */
    void addIncomingChannel(Object channelKey, Dispatch<T> dispatch);

    /**
     * Commences graceful stop of this connection. Stops accepting any more outgoing messages, and requests that the
     * peer stop sending incoming messages.
     */
    void requestStop();

    /**
     * Performs a graceful stop of this connection. Blocks until all dispatched incoming messages have been handled, and
     * all outgoing messages have been delivered.
     */
    void stop();
}
