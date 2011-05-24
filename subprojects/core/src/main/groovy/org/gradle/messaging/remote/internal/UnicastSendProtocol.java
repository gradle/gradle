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

import org.gradle.messaging.remote.internal.protocol.ConsumerAvailable;
import org.gradle.messaging.remote.internal.protocol.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class UnicastSendProtocol implements Protocol<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnicastSendProtocol.class);
    private final List<Object> queue = new ArrayList<Object>();
    private Object consumerId;
    private ProtocolContext<Object> context;
    private boolean stopping;

    public void start(ProtocolContext<Object> context) {
        this.context = context;
    }

    public void handleIncoming(Object message) {
        if (message instanceof ConsumerAvailable) {
            ConsumerAvailable consumerAvailable = (ConsumerAvailable) message;
            LOGGER.debug("Consumer available: {}", consumerAvailable);
            consumerId = consumerAvailable.getId();
            for (Object queued : queue) {
                context.dispatchOutgoing(new Request(consumerId, queued));
            }
            queue.clear();
            if (stopping) {
                LOGGER.debug("Queued messages dispatched. Stopping now.");
                context.stopped();
            }
        } else {
            throw new IllegalArgumentException(String.format("Received unexpected incoming message: %s", message));
        }
    }

    public void handleOutgoing(Object message) {
        if (consumerId == null) {
            queue.add(message);
        } else {
            context.dispatchOutgoing(new Request(consumerId, message));
        }
    }

    public void stopRequested() {
        if (queue.isEmpty()) {
            context.stopped();
            return;
        }

        LOGGER.debug("Waiting for outgoing messages to be dispatched to a consumer.");
        stopping = true;
        context.stopLater();
    }
}
