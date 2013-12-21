/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.messaging.remote.internal.hub;

import org.gradle.messaging.remote.internal.hub.protocol.ChannelMessage;
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.remote.internal.hub.protocol.RejectedMessage;
import org.gradle.messaging.remote.internal.hub.queue.MultiEndPointQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

class OutgoingQueue extends MultiEndPointQueue {
    private final IncomingQueue incomingQueue;

    OutgoingQueue(IncomingQueue incomingQueue, Lock lock) {
        super(lock);
        this.incomingQueue = incomingQueue;
    }

    void endOutput() {
        dispatch(new EndOfStream());
    }

    void discardQueued() {
        List<InterHubMessage> rejected = new ArrayList<InterHubMessage>();
        drain(rejected);
        for (InterHubMessage message : rejected) {
            if (message instanceof ChannelMessage) {
                ChannelMessage channelMessage = (ChannelMessage) message;
                incomingQueue.queue(new RejectedMessage(channelMessage.getChannel(), channelMessage.getPayload()));
            }
        }
    }
}
