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

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;

import java.util.*;
import java.util.concurrent.locks.Lock;

// TODO - use circular buffers to avoid copying
// TODO - share a single initializer with MultiChannelQueue
public class MultiEndPointQueue implements Dispatch<InterHubMessage> {
    private final Set<EndPointQueue> endpoints = new HashSet<EndPointQueue>();
    private final List<InterHubMessage> queue = new ArrayList<InterHubMessage>();
    private final List<EndPointQueue> waiting = new ArrayList<EndPointQueue>();
    private final Lock lock;
    private final QueueInitializer initializer = new QueueInitializer();

    public MultiEndPointQueue(Lock lock) {
        this.lock = lock;
    }

    public void dispatch(InterHubMessage message) {
        queue.add(message);
        flush();
    }

    void empty(EndPointQueue endPointQueue) {
        waiting.add(endPointQueue);
        flush();
    }

    void stopped(EndPointQueue queue) {
        waiting.remove(queue);
        endpoints.remove(queue);
        queue.dispatch(new EndOfStream());
    }

    public void drain(Collection<InterHubMessage> drainTo) {
        drainTo.addAll(queue);
        queue.clear();
    }

    private void flush() {
        // TODO - need to do a better job of routing messages when there are multiple endpoints. This is just going to forward all queued messages to the first
        // waiting endpoint, even if there are multiple waiting to do work
        EndPointQueue selected = waiting.isEmpty() ? null : waiting.get(0);
        while (!queue.isEmpty()) {
            InterHubMessage message = queue.get(0);
            switch (message.getDelivery()) {
                case Stateful:
                case AllHandlers:
                    if (endpoints.isEmpty()) {
                        return;
                    }
                    if (message.getDelivery() == InterHubMessage.Delivery.Stateful) {
                        initializer.onStatefulMessage(message);
                    }
                    for (EndPointQueue endpoint : endpoints) {
                        endpoint.dispatch(message);
                    }
                    queue.remove(0);
                    waiting.clear();
                    continue;
                case SingleHandler:
                    if (selected == null) {
                        return;
                    }
                    queue.remove(0);
                    waiting.remove(selected);
                    selected.dispatch(message);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown delivery type: " + message.getDelivery());
            }
        }
    }

    public EndPointQueue newEndpoint() {
        EndPointQueue endPointQueue = new EndPointQueue(this, lock.newCondition());
        endpoints.add(endPointQueue);
        initializer.onQueueAdded(endPointQueue);
        return endPointQueue;
    }
}
