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

import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.dispatch.AsyncDispatch;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.remote.internal.protocol.ChannelMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class IncomingDemultiplex implements Dispatch<Object>, Stoppable {
    private final Lock queueLock = new ReentrantLock();
    private final Map<Object, AsyncDispatch<Object>> incomingQueues = new HashMap<Object, AsyncDispatch<Object>>();
    private final Executor executor;

    public IncomingDemultiplex(Executor executor) {
        this.executor = executor;
    }

    public void dispatch(Object message) {
        ChannelMessage channelMessage = (ChannelMessage) message;
        Dispatch<Object> channel = findChannel(channelMessage.getChannel());
        channel.dispatch(channelMessage.getPayload());
    }

    public void addIncomingChannel(Object channel, Dispatch<Object> dispatch) {
        AsyncDispatch<Object> queue = findChannel(channel);
        queue.dispatchTo(dispatch);
    }

    private AsyncDispatch<Object> findChannel(Object channel) {
        AsyncDispatch<Object> queue;
        queueLock.lock();
        try {
            queue = incomingQueues.get(channel);
            if (queue == null) {
                queue = new AsyncDispatch<Object>(executor);
                incomingQueues.put(channel, queue);
            }
        } finally {
            queueLock.unlock();
        }
        return queue;
    }

    public void stop() {
        Stoppable stopper;
        queueLock.lock();
        try {
            stopper = new CompositeStoppable(incomingQueues.values());
            incomingQueues.clear();
        } finally {
            queueLock.unlock();
        }

        stopper.stop();
    }
}
