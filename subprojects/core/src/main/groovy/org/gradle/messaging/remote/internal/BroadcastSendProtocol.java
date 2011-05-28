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
import org.gradle.messaging.remote.internal.protocol.ConsumerUnavailable;
import org.gradle.messaging.remote.internal.protocol.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BroadcastSendProtocol implements Protocol<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastSendProtocol.class);
    private ProtocolContext<Message> context;
    private final Set<Object> consumers = new HashSet<Object>();
    private final List<Object> queue = new ArrayList<Object>();
    private boolean stopping;

    public void start(ProtocolContext<Message> context) {
        this.context = context;
    }

    public void handleOutgoing(Message message) {
        if (message instanceof Request) {
            Request request = (Request) message;
            if (consumers.isEmpty()) {
                queue.add(request.getPayload());
            } else {
                for (Object consumer : consumers) {
                    context.dispatchOutgoing(new Request(consumer, request.getPayload()));
                }
            }
        } else {
            throw new IllegalArgumentException(String.format("Unexpected outgoing message dispatched: %s", message));
        }
    }

    public void handleIncoming(Message message) {
        if (message instanceof ConsumerAvailable) {
            ConsumerAvailable consumerAvailable = (ConsumerAvailable) message;
            consumers.add(consumerAvailable.getId());
            if (!queue.isEmpty()) {
                for (Object queued : queue) {
                    context.dispatchOutgoing(new Request(consumerAvailable.getId(), queued));
                }
                queue.clear();
                if (stopping) {
                    LOGGER.debug("All queued outgoing messages have been dispatched. Stopping now.");
                    context.stopped();
                }
            }
        } else if (message instanceof ConsumerUnavailable) {
            ConsumerUnavailable consumerUnavailable = (ConsumerUnavailable) message;
            consumers.remove(consumerUnavailable.getId());
        } else {
            throw new IllegalArgumentException(String.format("Received unexpected incoming message: %s", message));
        }
    }

    public void stopRequested() {
        if (queue.isEmpty()) {
            LOGGER.debug("No outgoing messages queued. Stopping now.");
            context.stopped();
            return;
        }

        LOGGER.debug("Outgoing messages queued. Stopping later.");
        stopping = true;
        context.stopLater();
        context.callbackLater(5, TimeUnit.SECONDS, new Runnable() {
            public void run() {
                LOGGER.debug("Timeout waiting for queued messages to be dispatched. Stopping now.");
                queue.clear();
                context.stopped();
            }
        });
    }
}
