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

import org.gradle.messaging.remote.internal.protocol.ChannelMessage;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Protocol} implementation which routes messages for a channel via a channel-specific {@link Protocol} instance.
 */
public class InstancePerChannelProtocolAdapter<T> implements Protocol<Message> {
    private final Class<T> type;
    private final ChannelProtocolFactory<T> factory;
    private ProtocolContext<Message> context;
    private final Set<String> initialChannels = new HashSet<String>();
    private Map<String, ProtocolContextAdapter> channels = new HashMap<String, ProtocolContextAdapter>();
    private boolean stopping;

    public InstancePerChannelProtocolAdapter(Class<T> type, ChannelProtocolFactory<T> factory, String... initialChannels) {
        this.type = type;
        this.factory = factory;
        this.initialChannels.addAll(Arrays.asList(initialChannels));
    }

    public void start(ProtocolContext<Message> context) {
        this.context = context;
        for (String initialChannel : initialChannels) {
            findChannel(initialChannel);
        }
    }

    public void handleOutgoing(Message message) {
        ChannelMessage channelMessage = (ChannelMessage) message;
        findChannel(channelMessage).handleOutgoing(type.cast(channelMessage.getPayload()));
    }

    public void handleIncoming(Message message) {
        ChannelMessage channelMessage = (ChannelMessage) message;
        findChannel(channelMessage).handleIncoming(type.cast(channelMessage.getPayload()));
    }

    public void stopRequested() {
        stopping = true;
        for (ProtocolContextAdapter adapter : new ArrayList<ProtocolContextAdapter>(channels.values())) {
            adapter.stopRequested();
        }
        if (!channels.isEmpty()) {
            context.stopLater();
        }
    }

    private Protocol<T> findChannel(ChannelMessage message) {
        return findChannel(message.getChannel());
    }

    private Protocol<T> findChannel(String channel) {
        ProtocolContextAdapter adapter = channels.get(channel);
        if (adapter == null) {
            Protocol<T> protocol = factory.newChannel(channel);
            adapter = new ProtocolContextAdapter(channel, protocol);
            channels.put(channel, adapter);
            protocol.start(adapter);
            if (stopping) {
                adapter.stopRequested();
            }
        }
        return adapter.protocol;
    }

    private void channelStopped(ProtocolContextAdapter adapter) {
        channels.values().remove(adapter);
        if (channels.isEmpty()) {
            context.stopped();
        }
    }

    interface ChannelProtocolFactory<T> {
        Protocol<T> newChannel(Object channelKey);
    }

    private class ProtocolContextAdapter implements ProtocolContext<T> {
        private final String channel;
        private final Protocol<T> protocol;
        private boolean stoppingLater;
        private boolean stopped;

        private ProtocolContextAdapter(String channel, Protocol<T> protocol) {
            this.channel = channel;
            this.protocol = protocol;
        }

        public void dispatchOutgoing(T message) {
            context.dispatchOutgoing(new ChannelMessage(channel, message));
        }

        public void dispatchIncoming(T message) {
            context.dispatchIncoming(new ChannelMessage(channel, message));
        }

        public Callback callbackLater(int delay, TimeUnit delayUnits, Runnable action) {
            return context.callbackLater(delay, delayUnits, action);
        }

        public void stopLater() {
            stoppingLater = true;
        }

        public void stopped() {
            if (!stopped) {
                stopped = true;
                channelStopped(this);
            }
        }

        public void stopRequested() {
            protocol.stopRequested();
            if (!stoppingLater) {
                stopped();
            }
        }
    }

}
