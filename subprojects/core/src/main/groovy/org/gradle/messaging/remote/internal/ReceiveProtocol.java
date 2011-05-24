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

import org.gradle.messaging.remote.internal.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class ReceiveProtocol implements Protocol<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveProtocol.class);
    private final Object id;
    private final String displayName;
    private final Set<Object> producers = new HashSet<Object>();
    private ProtocolContext<Message> context;
    private boolean stopping;

    public ReceiveProtocol(Object id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public void start(ProtocolContext<Message> context) {
        this.context = context;
        context.dispatchIncoming(new ConsumerAvailable(id, displayName));
    }

    public void handleOutgoing(Message message) {
        if (message instanceof ProducerReady) {
            LOGGER.debug("Producer ready: {}", message);
            ProducerReady producerReady = (ProducerReady) message;
            producers.add(producerReady.getProducerId());
            context.dispatchIncoming(new ConsumerReady(id, producerReady.getProducerId()));
        } else if (message instanceof ProducerStopped) {
            LOGGER.debug("Producer stopped: {}", message);
            ProducerStopped producerStopped = (ProducerStopped) message;
            producers.remove(producerStopped.getProducerId());
            context.dispatchIncoming(new ConsumerStopped(id, producerStopped.getProducerId()));
            if (stopping && producers.isEmpty()) {
                LOGGER.debug("All producers finished. Stopping now.");
                stopped();
            }
        } else if (message instanceof ConsumerAvailable || message instanceof ConsumerUnavailable) {
            // Ignore these broadcasts
            return;
        } else if (message instanceof RoutableMessage) {
            context.dispatchOutgoing(message);
        } else {
            throw new IllegalArgumentException(String.format("Unexpected incoming message received: %s", message));
        }
    }

    public void handleIncoming(Message message) {
        context.dispatchIncoming(message);
    }

    public void stopRequested() {
        stopping = true;
        if (producers.isEmpty()) {
            LOGGER.debug("No producers. Stopping now.");
            stopped();
            return;
        }

        LOGGER.debug("Waiting for producers to finish. Stopping later. Producers: {}", producers);
        context.stopLater();
        for (Object producer : producers) {
            context.dispatchIncoming(new ConsumerStopping(id, producer));
        }
    }

    private void stopped() {
        context.dispatchIncoming(new ConsumerUnavailable(id));
        context.stopped();
    }
}
