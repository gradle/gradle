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
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.dispatch.*;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.protocol.ChannelAvailable;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.gradle.messaging.remote.internal.protocol.LookupRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultOutgoingBroadcast implements OutgoingBroadcast, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOutgoingBroadcast.class);
    private final String group;
    private final OutgoingConnector<Object> outgoingConnector;
    private final ExecutorFactory executor;
    private final ProtocolStack<DiscoveryMessage> protocolStack;
    private final Lock lock = new ReentrantLock();
    private final Set<String> pending = new HashSet<String>();
    private final Map<String, AsyncDispatch<MethodInvocation>> channels = new HashMap<String, AsyncDispatch<MethodInvocation>>();
    private final Map<Address, Connection<Object>> connections = new HashMap<Address, Connection<Object>>();

    public DefaultOutgoingBroadcast(String group, Connection<DiscoveryMessage> channel, OutgoingConnector<Object> outgoingConnector, ExecutorFactory executor) {
        this.group = group;
        this.outgoingConnector = outgoingConnector;
        this.executor = executor;
        DiscardingFailureHandler<DiscoveryMessage> failureHandler = new DiscardingFailureHandler<DiscoveryMessage>(LOGGER);
        protocolStack = new ProtocolStack<DiscoveryMessage>(channel, channel, executor.create("discovery protocol"), failureHandler, failureHandler, failureHandler, new ChannelLookupProtocol());
        protocolStack.receiveOn(new DiscoveryMessageDispatch());
    }

    public <T> T addOutgoing(Class<T> type) {
        String channelKey = type.getName();
        lock.lock();
        try {
            AsyncDispatch<MethodInvocation> channel = channels.get(channelKey);
            if (channel == null) {
                channel = new AsyncDispatch<MethodInvocation>(executor.create(String.format("outgoing %s", type.getSimpleName())));
                channels.put(channelKey, channel);
                if (pending.add(channelKey)) {
                    protocolStack.dispatch(new LookupRequest(group, channelKey));
                }
            }
            return new ProxyDispatchAdapter<T>(type, channel).getSource();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            new CompositeStoppable().add(protocolStack).add(channels.values()).add(connections.values()).stop();
        } finally {
            channels.clear();
            connections.clear();
            lock.unlock();
        }
    }

    private class DiscoveryMessageDispatch implements Dispatch<DiscoveryMessage> {
        public void dispatch(DiscoveryMessage message) {
            if (message instanceof ChannelAvailable) {
                ChannelAvailable available = (ChannelAvailable) message;
                Address serviceAddress = available.getAddress();
                AsyncDispatch<MethodInvocation> channel = null;
                Connection<Object> connection = null;
                lock.lock();
                try {
                    if (pending.remove(available.getChannel())) {
                        channel = channels.get(available.getChannel());
                    }
                    connection = connections.get(serviceAddress);
                } finally {
                    lock.unlock();
                }
                if (channel == null) {
                    return;
                }

                if (connection == null) {
                    connection = outgoingConnector.connect(serviceAddress);
                }

                lock.lock();
                try {
                    connections.put(serviceAddress, connection);
                } finally {
                    lock.unlock();
                }

                channel.dispatchTo(new MethodInvocationMarshallingDispatch(new OutgoingMultiplex(available.getChannel(), connection)));
            }
        }
    }
}
