/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.messaging.concurrent.*;
import org.gradle.messaging.dispatch.*;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class DefaultMultiChannelConnection implements MultiChannelConnection<Message> {
    private final URI sourceAddress;
    private final URI destinationAddress;
    private final EndOfStreamDispatch outgoingDispatch;
    private final AsyncDispatch<Message> outgoingQueue;
    private final AsyncReceive<Message> incomingReceive;
    private final EndOfStreamFilter incomingDispatch;
    private final IncomingDemultiplex incomingDemux;
    private final StoppableExecutor executor;
    private final Connection<Message> connection;

    DefaultMultiChannelConnection(ExecutorFactory executorFactory, String displayName, final Connection<Message> connection, URI sourceAddress, URI destinationAddress) {
        this.connection = connection;
        this.executor = executorFactory.create(displayName);

        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;

        // Outgoing pipeline: <source> -> <channel-mux> -> <end-of-stream-dispatch> -> <async-queue> -> <ignore-failures> -> <connection>
        outgoingQueue = new AsyncDispatch<Message>(executor);
        outgoingQueue.dispatchTo(wrapFailures(connection));
        outgoingDispatch = new EndOfStreamDispatch(new ChannelMessageMarshallingDispatch(outgoingQueue));

        // Incoming pipeline: <connection> -> <async-receive> -> <ignore-failures> -> <end-of-stream-filter> -> <channel-demux> -> <channel-async-queue> -> <ignore-failures> -> <handler>
        incomingDemux = new IncomingDemultiplex();
        incomingDispatch = new EndOfStreamFilter(incomingDemux, new Runnable() {
            public void run() {
                requestStop();
            }
        });
        incomingReceive = new AsyncReceive<Message>(executor, wrapFailures(new ChannelMessageUnmarshallingDispatch(incomingDispatch)));
        incomingReceive.receiveFrom(new EndOfStreamReceive(connection));
    }

    private Dispatch<Message> wrapFailures(Dispatch<Message> dispatch) {
        return new DiscardOnFailureDispatch<Message>(dispatch, LoggerFactory.getLogger(
                DefaultMultiChannelConnector.class));
    }

    public URI getLocalAddress() {
        if (sourceAddress == null) {
            throw new UnsupportedOperationException();
        }
        return sourceAddress;
    }

    public URI getRemoteAddress() {
        if (destinationAddress == null) {
            throw new UnsupportedOperationException();
        }
        return destinationAddress;
    }

    public void requestStop() {
        outgoingDispatch.stop();
    }

    public void addIncomingChannel(Object channelKey, Dispatch<Message> dispatch) {
        incomingDemux.addIncomingChannel(channelKey, wrapFailures(dispatch));
    }

    public Dispatch<Message> addOutgoingChannel(Object channelKey) {
        return new OutgoingMultiplex(channelKey, outgoingDispatch);
    }

    public void stop() {
        executor.execute(new Runnable() {
            public void run() {
                // End-of-stream handshake
                requestStop();
                incomingDispatch.stop();

                // Flush queues (should be empty)
                incomingReceive.requestStop();
                outgoingQueue.requestStop();
                new CompositeStoppable(outgoingQueue, connection, incomingReceive, incomingDemux).stop();
            }
        });
        try {
            executor.stop(120, TimeUnit.SECONDS);
        } catch (Throwable e) {
            throw new GradleException("Could not stop connection.", e);
        }
    }

    private class IncomingDemultiplex implements Dispatch<Message>, Stoppable {
        private final Lock queueLock = new ReentrantLock();
        private final Map<Object, AsyncDispatch<Message>> incomingQueues
                = new HashMap<Object, AsyncDispatch<Message>>();

        public void dispatch(Message message) {
            ChannelMessage channelMessage = (ChannelMessage) message;
            Dispatch<Message> channel = findChannel(channelMessage.getChannel());
            channel.dispatch(channelMessage.getPayload());
        }

        public void addIncomingChannel(Object channel, Dispatch<Message> dispatch) {
            AsyncDispatch<Message> queue = findChannel(channel);
            queue.dispatchTo(dispatch);
        }

        private AsyncDispatch<Message> findChannel(Object channel) {
            AsyncDispatch<Message> queue;
            queueLock.lock();
            try {
                queue = incomingQueues.get(channel);
                if (queue == null) {
                    queue = new AsyncDispatch<Message>(executor);
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

    private static class OutgoingMultiplex implements Dispatch<Message> {
        private final Dispatch<Message> dispatch;
        private final Object channelKey;

        private OutgoingMultiplex(Object channelKey, Dispatch<Message> dispatch) {
            this.channelKey = channelKey;
            this.dispatch = dispatch;
        }

        public void dispatch(Message message) {
            dispatch.dispatch(new ChannelMessage(channelKey, message));
        }
    }
}
