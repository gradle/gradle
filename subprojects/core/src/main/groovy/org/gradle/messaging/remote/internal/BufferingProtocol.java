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

import org.gradle.messaging.remote.internal.protocol.MessageCredits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public class BufferingProtocol implements Protocol<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferingProtocol.class);
    private final int maxBufferSize;
    private final LinkedList<Message> queue = new LinkedList<Message>();
    private ProtocolContext<Message> context;
    private int remainingOutgoingCredits;
    private int remainingIncomingCredits;
    private boolean stopping;

    public BufferingProtocol(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    public void start(ProtocolContext<Message> context) {
        this.context = context;
        context.dispatchOutgoing(new MessageCredits(maxBufferSize));
        remainingIncomingCredits = maxBufferSize;
    }

    public void handleIncoming(Message message) {
        remainingIncomingCredits--;
        if (remainingOutgoingCredits == 0) {
            queue.add(message);
        } else {
            remainingOutgoingCredits--;
            context.dispatchIncoming(message);
        }
        maybeGrantIncomingCredits();
    }

    public void handleOutgoing(Message message) {
        if (message instanceof MessageCredits) {
            MessageCredits credits = (MessageCredits) message;
            remainingOutgoingCredits += credits.getCredits();
            while (!queue.isEmpty() && remainingOutgoingCredits > 0) {
                remainingOutgoingCredits--;
                context.dispatchIncoming(queue.removeFirst());
            }
            if (stopping && queue.isEmpty()) {
                context.stopped();
                return;
            }
            maybeGrantIncomingCredits();
        } else {
            context.dispatchOutgoing(message);
        }
    }

    private void maybeGrantIncomingCredits() {
        int minBatchSize = maxBufferSize / 2;
        int grantablePermits = maxBufferSize - queue.size() - remainingIncomingCredits + remainingOutgoingCredits;
        if (grantablePermits >= minBatchSize) {
            context.dispatchOutgoing(new MessageCredits(grantablePermits));
            remainingIncomingCredits += grantablePermits;
        }
    }

    public void stopRequested() {
        stopping = true;
        if (queue.isEmpty()) {
            context.stopped();
        } else {
            LOGGER.debug("Waiting for queue to empty. Stopping later.");
            context.stopLater();
        }
    }
}
