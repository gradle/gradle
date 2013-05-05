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

package org.gradle.messaging.remote.internal.hub.queue;

import org.gradle.messaging.remote.internal.hub.protocol.ChannelIdentifier;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.remote.internal.hub.protocol.Routable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import static org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage.Delivery.Stateful;

public class MultiChannelQueue {
    private final Lock lock;
    private final Map<ChannelIdentifier, MultiEndPointQueue> channels = new HashMap<ChannelIdentifier, MultiEndPointQueue>();
    private final QueueInitializer initializer = new QueueInitializer();

    public MultiChannelQueue(Lock lock) {
        this.lock = lock;
    }

    public MultiEndPointQueue getChannel(ChannelIdentifier channel) {
        MultiEndPointQueue queue = channels.get(channel);
        if (queue == null) {
            queue = new MultiEndPointQueue(lock);
            channels.put(channel, queue);
            initializer.onQueueAdded(queue);
        }
        return queue;
    }

    public void queue(InterHubMessage message) {
        if (message.getDelivery() == Stateful) {
            initializer.onStatefulMessage(message);
        }
        if (message instanceof Routable) {
            Routable routableMessage = (Routable) message;
            getChannel(routableMessage.getChannel()).dispatch(message);
        } else if (message.getDelivery() == Stateful || message.getDelivery() == InterHubMessage.Delivery.AllHandlers) {
            for (MultiEndPointQueue queue : channels.values()) {
                queue.dispatch(message);
            }
        } else {
            throw new IllegalArgumentException(String.format("Don't know how to route message %s.", message));
        }
    }
}
