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

package org.gradle.internal.remote.internal

import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class TestConnection implements RemoteConnection<InterHubMessage> {
    private static final Object END = new Object()
    private final BlockingQueue<Object> incoming = new LinkedBlockingQueue<>()
    private final BlockingQueue<InterHubMessage> outgoing = new LinkedBlockingQueue<>()
    private final BlockingQueue<InterHubMessage> outgoingBuffered = new LinkedBlockingQueue<>()

    @Override
    void dispatch(InterHubMessage message) {
        outgoingBuffered.put(message)
    }

    @Override
    void flush() {
        outgoingBuffered.drainTo(outgoing)
    }

    @Override
    InterHubMessage receive() {
        def message = incoming.take()
        return message == END ? null : (InterHubMessage) message
    }

    /**
     * Queues the given message to return from {@link #receive()}.
     */
    void queueIncoming(InterHubMessage message) {
        incoming.put(message)
    }

    /**
     * Marks the end of the incoming messages.
     */
    @Override
    void stop() {
        incoming.put(END)
    }
}
