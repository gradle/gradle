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

import org.gradle.api.Action;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.AsyncReceive;
import org.gradle.messaging.dispatch.DiscardingFailureHandler;
import org.gradle.messaging.dispatch.ReflectionDispatch;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.internal.protocol.ChannelAvailable;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultIncomingBroadcast implements IncomingBroadcast, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncomingBroadcast.class);
    private final ProtocolStack<DiscoveryMessage> protocolStack;
    private final String group;
    private final Lock lock = new ReentrantLock();
    private final Set<String> channels = new HashSet<String>();
    private final Set<Connection<Object>> connections = new HashSet<Connection<Object>>();
    private final StoppableExecutor executor;
    private final IncomingDemultiplex demultiplex;
    private final AsyncReceive<Object> asyncReceive;
    private final Address address;

    public DefaultIncomingBroadcast(String group, AsyncConnection<DiscoveryMessage> connection, IncomingConnector<Object> incomingConnector, ExecutorFactory executorFactory) {
        this.group = group;
        executor = executorFactory.create("incoming broadcast");
        DiscardingFailureHandler<DiscoveryMessage> failureHandler = new DiscardingFailureHandler<DiscoveryMessage>(LOGGER);
        protocolStack = new ProtocolStack<DiscoveryMessage>(executor, failureHandler, failureHandler, new ChannelRegistrationProtocol());
        connection.receiveOn(protocolStack.getBottom());
        protocolStack.getBottom().receiveOn(connection);
        demultiplex = new IncomingDemultiplex(executor);
        asyncReceive = new AsyncReceive<Object>(executor, demultiplex);
        address = incomingConnector.accept(new IncomingConnectionAction());
    }

    public <T> void addIncoming(Class<T> type, T handler) {
        String channelKey = type.getName();
        lock.lock();
        try {
            if (channels.add(channelKey)) {
                protocolStack.getTop().dispatch(new ChannelAvailable(group, channelKey, address));
            }
            demultiplex.addIncomingChannel(channelKey, new MethodInvocationUnmarshallingDispatch(new ReflectionDispatch(handler), type.getClassLoader()));
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            new CompositeStoppable(protocolStack).add(connections).add(asyncReceive, demultiplex, executor).stop();
        } finally {
            channels.clear();
            connections.clear();
            lock.unlock();
        }
    }

    private class IncomingConnectionAction implements Action<ConnectEvent<Connection<Object>>> {
        public void execute(ConnectEvent<Connection<Object>> connectionConnectEvent) {
            lock.lock();
            try {
                connections.add(connectionConnectEvent.getConnection());
                asyncReceive.receiveFrom(connectionConnectEvent.getConnection());
            } finally {
                lock.unlock();
            }
        }
    }

}
