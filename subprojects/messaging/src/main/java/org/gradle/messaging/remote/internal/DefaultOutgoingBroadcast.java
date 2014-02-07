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

import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.id.IdGenerator;
import org.gradle.messaging.dispatch.DiscardingFailureHandler;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.DispatchFailureHandler;
import org.gradle.messaging.dispatch.ProxyDispatchAdapter;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.protocol.ChannelAvailable;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.gradle.messaging.remote.internal.protocol.LookupRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultOutgoingBroadcast implements OutgoingBroadcast, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOutgoingBroadcast.class);
    private final MessageOriginator messageOriginator;
    private final String group;
    private final OutgoingConnector outgoingConnector;
    private final ProtocolStack<DiscoveryMessage> discoveryBroadcast;
    private final Lock lock = new ReentrantLock();
    private final StoppableExecutor executor;
    private final Set<String> channels = new HashSet<String>();
    private final Set<Address> connections = new HashSet<Address>();
    private final MessageHub hub;

    public DefaultOutgoingBroadcast(MessageOriginator messageOriginator, String group, AsyncConnection<DiscoveryMessage> connection, OutgoingConnector outgoingConnector, ExecutorFactory executorFactory, final IdGenerator<UUID> idGenerator, ClassLoader messagingClassLoader) {
        this.messageOriginator = messageOriginator;
        this.group = group;
        this.outgoingConnector = outgoingConnector;
        DispatchFailureHandler<Object> failureHandler = new DiscardingFailureHandler<Object>(LOGGER);

        hub = new MessageHub("outgoing broadcast", messageOriginator.getName(), executorFactory, idGenerator, messagingClassLoader);

        executor = executorFactory.create("broadcast lookup");
        discoveryBroadcast = new ProtocolStack<DiscoveryMessage>(executor, failureHandler, failureHandler, new ChannelLookupProtocol());
        connection.dispatchTo(new GroupMessageFilter(group, discoveryBroadcast.getBottom()));
        discoveryBroadcast.getBottom().dispatchTo(connection);
        discoveryBroadcast.getTop().dispatchTo(new DiscoveryMessageDispatch());

        LOGGER.info("Created OutgoingBroadcast with {}", messageOriginator);
    }

    public <T> T addOutgoing(Class<T> type) {
        String channelKey = type.getName();
        lock.lock();
        try {
            if (channels.add(channelKey)) {
                discoveryBroadcast.getTop().dispatch(new LookupRequest(messageOriginator, group, channelKey));
            }
        } finally {
            lock.unlock();
        }
        return new ProxyDispatchAdapter<T>(hub.addMulticastOutgoing(channelKey), type).getSource();
    }

    public void stop() {
        CompositeStoppable stoppable;
        lock.lock();
        try {
            stoppable = CompositeStoppable.stoppable(hub, discoveryBroadcast, executor);
        } finally {
            connections.clear();
            lock.unlock();
        }
        stoppable.stop();
    }

    private class DiscoveryMessageDispatch implements Dispatch<DiscoveryMessage> {
        public void dispatch(DiscoveryMessage message) {
            if (message instanceof ChannelAvailable) {
                ChannelAvailable available = (ChannelAvailable) message;
                Address serviceAddress = available.getAddress();
                lock.lock();
                try {
                    if (!channels.contains(available.getChannel())) {
                        return;
                    }
                    if (connections.contains(serviceAddress)) {
                        return;
                    }
                    connections.add(serviceAddress);
                } finally {
                    lock.unlock();
                }

                Connection<Message> syncConnection = outgoingConnector.connect(serviceAddress).create(DiscoveryMessage.class.getClassLoader());
                hub.addConnection(syncConnection);
            }
        }
    }
}
