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

public class EndOfStreamHandshakeProtocol implements Protocol<Object> {
    private final Runnable action;
    private ProtocolContext<Object> context;
    private boolean eosSent;
    private boolean eosReceived;

    public EndOfStreamHandshakeProtocol(Runnable action) {
        this.action = action;
    }

    public void start(ProtocolContext<Object> context) {
        this.context = context;
    }

    public void handleIncoming(Object message) {
        if (message instanceof EndOfStreamEvent) {
            eosReceived = true;
            if (eosSent) {
                context.stopped();
            } else {
                eosSent = true;
                context.dispatchOutgoing(new EndOfStreamEvent());
            }
            action.run();
            return;
        }
        context.dispatchIncoming(message);
    }

    public void handleOutgoing(Object message) {
        context.dispatchOutgoing(message);
    }

    public void stopRequested() {
        if (eosSent && eosReceived) {
            context.stopped();
            return;
        }
        if (!eosSent) {
            context.stopLater();
            context.dispatchOutgoing(new EndOfStreamEvent());
            eosSent = true;
        }
    }
}
