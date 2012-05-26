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

import java.util.*;

public class SendProtocol implements Protocol<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SendProtocol.class);
    private final String channelKey;
    private final UUID id;
    private final String displayName;
    private ProtocolContext<Message> context;
    private boolean stopping;
    private final Map<Object, ConsumerAvailable> pending = new HashMap<Object, ConsumerAvailable>();
    private final Set<Object> consumers = new HashSet<Object>();

    public SendProtocol(UUID id, String displayName, String channelKey) {
        this.channelKey = channelKey;
        this.id = id;
        this.displayName = displayName;
    }

    public void start(ProtocolContext<Message> context) {
        LOGGER.debug("Starting producer {}", id);
        this.context = context;
        context.dispatchOutgoing(new ProducerAvailable(id, displayName, channelKey));
    }

    public void handleIncoming(Message message) {
        if (message instanceof ConsumerAvailable) {
            LOGGER.debug("Consumer available: {}", message);
            ConsumerAvailable consumerAvailable = (ConsumerAvailable) message;
            pending.put(consumerAvailable.getId(), consumerAvailable);
            consumers.add(consumerAvailable.getId());
            context.dispatchOutgoing(new ProducerReady(id, consumerAvailable.getId()));
        } else if (message instanceof ConsumerReady) {
            LOGGER.debug("Consumer ready: {}", message);
            ConsumerReady consumerReady = (ConsumerReady) message;
            context.dispatchIncoming(pending.remove(consumerReady.getConsumerId()));
        } else if (message instanceof ConsumerStopping) {
            LOGGER.debug("Consumer stopping: {}", message);
            ConsumerStopping consumerStopping = (ConsumerStopping) message;
            context.dispatchIncoming(new ConsumerUnavailable(consumerStopping.getConsumerId()));
            context.dispatchOutgoing(new ProducerStopped(id, consumerStopping.getConsumerId()));
        } else if (message instanceof ConsumerStopped) {
            LOGGER.debug("Consumer stopped: {}", message);
            ConsumerStopped consumerStopped = (ConsumerStopped) message;
            consumers.remove(consumerStopped.getConsumerId());
            maybeStop();
        } else if (message instanceof ConsumerUnavailable) {
            LOGGER.debug("Consumer unavailable: {}", message);
            ConsumerUnavailable consumerUnavailable = (ConsumerUnavailable) message;
            consumers.remove(consumerUnavailable.getId());
            if (pending.remove(consumerUnavailable.getId()) == null) {
                context.dispatchIncoming(new ConsumerUnavailable(consumerUnavailable.getId()));
            }
            maybeStop();
        } else {
            throw new IllegalArgumentException(String.format("Unexpected incoming message received: %s", message));
        }
    }

    private void maybeStop() {
        if (consumers.isEmpty() && stopping) {
            LOGGER.debug("All consumers stopped. Stopping now.");
            context.dispatchOutgoing(new ProducerUnavailable(id));
            context.stopped();
        }
    }

    public void handleOutgoing(Message message) {
        if (message instanceof RoutableMessage) {
            RoutableMessage routableMessage = (RoutableMessage) message;
            if (!consumers.contains(routableMessage.getDestination())) {
                throw new IllegalStateException(String.format("Message to unexpected destination dispatched: %s", message));
            }
            context.dispatchOutgoing(message);
        } else {
            throw new IllegalArgumentException(String.format("Unexpected outgoing message dispatched: %s", message));
        }
    }

    public void stopRequested() {
        stopping = true;
        if (consumers.isEmpty()) {
            maybeStop();
            return;
        }

        LOGGER.debug("Waiting for consumers to stop: {}", consumers);
        context.stopLater();
        for (Object consumerId : consumers) {
            context.dispatchOutgoing(new ProducerStopped(id, consumerId));
        }
    }
}
