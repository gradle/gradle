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
import java.util.UUID;

public class ReceiveProtocol implements Protocol<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveProtocol.class);
    private final UUID id;
    private final String displayName;
    private final String channelKey;
    private final Set<Object> producers = new HashSet<Object>();
    private ProtocolContext<Message> context;
    private boolean stopping;

    public ReceiveProtocol(UUID id, String displayName, String channelKey) {
        this.id = id;
        this.displayName = displayName;
        this.channelKey = channelKey;
    }

    public void start(ProtocolContext<Message> context) {
        this.context = context;
        LOGGER.debug("Starting receiver {}.", id);
        context.dispatchOutgoing(new ConsumerAvailable(id, displayName, channelKey));
    }

    public void handleIncoming(Message message) {
        if (message instanceof ProducerReady) {
            LOGGER.debug("Producer ready: {}", message);
            ProducerReady producerReady = (ProducerReady) message;
            producers.add(producerReady.getProducerId());
            context.dispatchOutgoing(new ConsumerReady(id, producerReady.getProducerId()));
        } else if (message instanceof ProducerStopped) {
            LOGGER.debug("Producer stopped: {}", message);
            ProducerStopped producerStopped = (ProducerStopped) message;
            context.dispatchOutgoing(new ConsumerStopped(id, producerStopped.getProducerId()));
            removeProducer(producerStopped.getProducerId());
        } else if (message instanceof ProducerUnavailable) {
            LOGGER.debug("Producer unavailable: {}", message);
            ProducerUnavailable producerUnavailable = (ProducerUnavailable) message;
            removeProducer(producerUnavailable.getId());
        } else if (message instanceof ProducerAvailable) {
            // Ignore these broadcasts
            return;
        } else if (message instanceof Request) {
            context.dispatchIncoming(message);
        } else {
            throw new IllegalArgumentException(String.format("Unexpected incoming message received: %s", message));
        }
    }

    private void removeProducer(Object producerId) {
        producers.remove(producerId);
        if (stopping && producers.isEmpty()) {
            LOGGER.debug("All producers finished. Stopping now.");
            allProducersFinished();
        }
    }

    public void handleOutgoing(Message message) {
        if (message instanceof WorkerStopping) {
            workerStopped();
        } else if (message instanceof MessageCredits) {
            LOGGER.debug("Discarding {}.", message);
        } else {
            throw new IllegalArgumentException(String.format("Unexpected outgoing message dispatched: %s", message));
        }
    }

    private void workerStopped() {
        stopping = true;
        if (producers.isEmpty()) {
            LOGGER.debug("No producers. Stopping now.");
            allProducersFinished();
            return;
        }

        LOGGER.debug("Waiting for producers to finish. Stopping later. Producers: {}", producers);
        for (Object producer : producers) {
            context.dispatchOutgoing(new ConsumerStopping(id, producer));
        }
    }

    private void allProducersFinished() {
        context.dispatchOutgoing(new ConsumerUnavailable(id));
        context.dispatchIncoming(new EndOfStreamEvent());
    }

    public void stopRequested() {
        assert stopping;
        context.stopped();
    }
}
