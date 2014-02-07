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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.messaging.dispatch.DiscardingFailureHandler;
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.internal.hub.MessageHubBackedClient;
import org.gradle.messaging.remote.internal.hub.MessageHubBackedServer;
import org.gradle.messaging.remote.internal.inet.*;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.gradle.messaging.remote.internal.protocol.DiscoveryProtocolSerializer;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * A factory for a set of messaging services. Provides the following services:
 *
 * <ul>
 *
 * <li>{@link MessagingClient}</li>
 *
 * <li>{@link MessagingServer}</li>
 *
 * <li>{@link OutgoingBroadcast}</li>
 *
 * <li>{@link IncomingBroadcast}</li>
 *
 * </ul>
 */
public class MessagingServices extends DefaultServiceRegistry implements Stoppable {
    private final IdGenerator<UUID> idGenerator = new UUIDGenerator();
    private final ClassLoader messageClassLoader;
    private final String broadcastGroup;
    private final SocketInetAddress broadcastAddress;

    public MessagingServices(ClassLoader messageClassLoader) {
        this(messageClassLoader, "gradle");
    }

    public MessagingServices(ClassLoader messageClassLoader, String broadcastGroup) {
        this(messageClassLoader, broadcastGroup, defaultBroadcastAddress());
    }

    public MessagingServices(ClassLoader messageClassLoader, String broadcastGroup, SocketInetAddress broadcastAddress) {
        this.messageClassLoader = messageClassLoader;
        this.broadcastGroup = broadcastGroup;
        this.broadcastAddress = broadcastAddress;
    }

    private static SocketInetAddress defaultBroadcastAddress() {
        try {
            return new SocketInetAddress(InetAddress.getByName("233.253.17.122"), 7912);
        } catch (UnknownHostException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void stop() {
        close();
    }

    protected MessageOriginator createMessageOriginator() {
        String hostName = get(InetAddressFactory.class).getHostName();
        String nodeName = String.format("%s@%s", System.getProperty("user.name"), hostName);
        return new MessageOriginator(idGenerator.generateId(), nodeName);
    }

    protected ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    protected InetAddressFactory createInetAddressFactory() {
        return new InetAddressFactory();
    }

    protected OutgoingConnector createOutgoingConnector() {
        return new TcpOutgoingConnector();
    }

    protected IncomingConnector createIncomingConnector(ExecutorFactory executorFactory, InetAddressFactory inetAddressFactory) {
        return new TcpIncomingConnector(
                executorFactory,
                inetAddressFactory,
                idGenerator
        );
    }

    protected MessagingClient createMessagingClient(OutgoingConnector outgoingConnector, ExecutorFactory executorFactory) {
        return new MessageHubBackedClient(
                outgoingConnector,
                executorFactory);
    }

    protected MessagingServer createMessagingServer(IncomingConnector incomingConnector, ExecutorFactory executorFactory) {
        return new MessageHubBackedServer(
                incomingConnector,
                executorFactory);
    }

    protected IncomingBroadcast createIncomingBroadcast(MessageOriginator messageOriginator, AsyncConnection<DiscoveryMessage> asyncConnection, IncomingConnector incomingConnector, ExecutorFactory executorFactory) {
        return new DefaultIncomingBroadcast(
                messageOriginator,
                broadcastGroup,
                asyncConnection,
                incomingConnector,
                executorFactory,
                idGenerator,
                messageClassLoader);
    }

    protected OutgoingBroadcast createOutgoingBroadcast(MessageOriginator messageOriginator, AsyncConnection<DiscoveryMessage> asyncConnection, OutgoingConnector outgoingConnector, ExecutorFactory executorFactory) {
        return new DefaultOutgoingBroadcast(
                messageOriginator,
                broadcastGroup,
                asyncConnection,
                outgoingConnector,
                executorFactory,
                idGenerator,
                messageClassLoader);
    }

    protected AsyncConnection<DiscoveryMessage> createMulticastConnection(ExecutorFactory executorFactory) {
        MulticastConnection<DiscoveryMessage> connection = new MulticastConnection<DiscoveryMessage>(broadcastAddress, new DiscoveryProtocolSerializer());
        return new AsyncConnectionAdapter<DiscoveryMessage>(
                connection,
                new DiscardingFailureHandler<DiscoveryMessage>(LoggerFactory.getLogger(MulticastConnection.class)),
                executorFactory);
    }
}
