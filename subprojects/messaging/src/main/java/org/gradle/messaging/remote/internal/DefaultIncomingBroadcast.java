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
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.id.IdGenerator;
import org.gradle.messaging.dispatch.DiscardingFailureHandler;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ReflectionDispatch;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectionAcceptor;
import org.gradle.messaging.remote.internal.protocol.ChannelAvailable;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultIncomingBroadcast implements IncomingBroadcast, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncomingBroadcast.class);
    private final ProtocolStack<DiscoveryMessage> protocolStack;
    private final MessageOriginator messageOriginator;
    private final String group;
    private final Lock lock = new ReentrantLock();
    private final Set<String> channels = new HashSet<String>();
    private final StoppableExecutor executor;
    private final Address address;
    private final MessageHub hub;
    private final ConnectionAcceptor acceptor;

    public DefaultIncomingBroadcast(MessageOriginator messageOriginator, String group, AsyncConnection<DiscoveryMessage> connection, IncomingConnector incomingConnector, ExecutorFactory executorFactory, IdGenerator<UUID> idGenerator, ClassLoader messagingClassLoader) {
        this.messageOriginator = messageOriginator;
        this.group = group;

        executor = executorFactory.create("discovery broadcast");
        DiscardingFailureHandler<DiscoveryMessage> failureHandler = new DiscardingFailureHandler<DiscoveryMessage>(LOGGER);
        protocolStack = new ProtocolStack<DiscoveryMessage>(executor, failureHandler, failureHandler, new ChannelRegistrationProtocol(messageOriginator));
        connection.dispatchTo(new GroupMessageFilter(group, protocolStack.getBottom()));
        protocolStack.getBottom().dispatchTo(connection);

        acceptor = incomingConnector.accept(new IncomingConnectionAction(), true);
        address = acceptor.getAddress();
        hub = new MessageHub("incoming broadcast", messageOriginator.getName(), executorFactory, idGenerator, messagingClassLoader);

        LOGGER.info("Created IncomingBroadcast with {}", messageOriginator);
    }

    public <T> void addIncoming(Class<T> type, T handler) {
        String channelKey = type.getName();
        lock.lock();
        try {
            if (channels.add(channelKey)) {
                protocolStack.getTop().dispatch(new ChannelAvailable(messageOriginator, group, channelKey, address));
            }
        } finally {
            lock.unlock();
        }
        hub.addIncoming(channelKey, new TypeCastDispatch<MethodInvocation, Object>(MethodInvocation.class, new ReflectionDispatch(handler)));
    }

    public void stop() {
        CompositeStoppable.stoppable(acceptor, protocolStack, hub, executor).stop();
    }

    private class IncomingConnectionAction implements Action<ConnectCompletion> {
        public void execute(ConnectCompletion completion) {
            Connection<Message> connection = completion.create(getClass().getClassLoader());
            hub.addConnection(connection);
        }
    }
}
