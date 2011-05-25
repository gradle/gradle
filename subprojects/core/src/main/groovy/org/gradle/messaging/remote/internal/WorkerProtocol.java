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

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.remote.internal.protocol.Request;
import org.gradle.messaging.remote.internal.protocol.WorkerStopped;
import org.gradle.messaging.remote.internal.protocol.WorkerStopping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts a worker {@link Dispatch} to a messaging protocol.
 *
 * Note: this protocol blocks while messages are being dispatched. This means sending and receiving are stuck for the whole stack. Generally, this protocol should live in its own stack.
 */
public class WorkerProtocol implements Protocol<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerProtocol.class);
    private final Dispatch<Object> worker;
    private ProtocolContext<Object> context;

    public WorkerProtocol(Dispatch<Object> worker) {
        this.worker = worker;
    }

    public void start(ProtocolContext<Object> context) {
        this.context = context;
    }

    public void handleIncoming(Object message) {
        if (message instanceof WorkerStopped) {
            LOGGER.debug("Received worker stopped: {}", message);
            context.stopped();
        } else if (message instanceof Request) {
            Request request = (Request) message;
            LOGGER.debug("Dispatching request to worker: {}", message);
            worker.dispatch(request.getPayload());
        } else {
            throw new IllegalArgumentException(String.format("Unexpected incoming message received: %s", message));
        }
    }

    public void handleOutgoing(Object message) {
        throw new IllegalArgumentException(String.format("Unexpected outgoing message dispatched: %s", message));
    }

    public void stopRequested() {
        context.dispatchOutgoing(new WorkerStopping());
        context.stopLater();
    }
}
