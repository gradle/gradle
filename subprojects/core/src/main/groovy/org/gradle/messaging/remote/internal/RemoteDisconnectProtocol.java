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

import org.gradle.messaging.remote.internal.protocol.EndOfStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteDisconnectProtocol implements Protocol<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDisconnectProtocol.class);
    private ProtocolContext<Message> context;
    private boolean stopping;
    private boolean outgoingStopped;
    private boolean finished;

    public void start(ProtocolContext<Message> context) {
        this.context = context;
    }

    public void handleIncoming(Message message) {
        context.dispatchIncoming(message);
    }

    public void handleOutgoing(Message message) {
        if (message instanceof EndOfStreamEvent) {
            if (stopping) {
                context.stopped();
                return;
            }
            context.dispatchOutgoing(message);
            outgoingStopped = true;
            finished = true;
        } else if (!outgoingStopped) {
            context.dispatchOutgoing(message);
        } else {
            LOGGER.debug("Discarding outgoing message {} as output has been finished.", message);
        }
    }

    public void stopRequested() {
        if (finished) {
            context.stopped();
            return;
        }

        if (!outgoingStopped) {
            context.dispatchOutgoing(new EndOfStreamEvent());
            outgoingStopped = true;
        }
        context.stopLater();
        stopping = true;
    }
}
