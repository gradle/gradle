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

import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.Stoppable;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.messaging.dispatch.DiscardingFailureHandler;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.internal.hub.InterHubMessageSerializer;
import org.gradle.messaging.remote.internal.hub.MessageHubBackedClient;
import org.gradle.messaging.remote.internal.hub.MessageHubBackedServer;
import org.gradle.messaging.remote.internal.hub.MethodInvocationSerializer;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.remote.internal.inet.*;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.gradle.messaging.remote.internal.protocol.DiscoveryProtocolSerializer;
import org.gradle.messaging.serialize.kryo.JavaSerializer;
import org.gradle.messaging.serialize.kryo.TypeSafeSerializer;
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
    private MessagingClient messagingClient;
    private IncomingConnector<Message> incomingLegacyConnector;
    private IncomingConnector<InterHubMessage> incomingHubConnector;
    private DefaultExecutorFactory executorFactory;
    private MessagingServer messagingServer;
    private DefaultIncomingBroadcast incomingBroadcast;
    private AsyncConnectionAdapter<DiscoveryMessage> multicastConnection;
    private DefaultOutgoingBroadcast outgoingBroadcast;

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

    @Override
    public void close() {
        CompositeStoppable stoppable = new CompositeStoppable();
        stoppable.add(incomingLegacyConnector);
        stoppable.add(incomingHubConnector);
        stoppable.add(messagingClient);
        stoppable.add(messagingServer);
        stoppable.add(outgoingBroadcast);
        stoppable.add(incomingBroadcast);
        stoppable.add(multicastConnection);
        stoppable.add(executorFactory);
        stoppable.stop();
    }

    protected MessageOriginator createMessageOriginator() {
        String hostName = get(InetAddressFactory.class).getHostName();
        String nodeName = String.format("%s@%s", System.getProperty("user.name"), hostName);
        return new MessageOriginator(idGenerator.generateId(), nodeName);
    }

    protected ExecutorFactory createExecutorFactory() {
        executorFactory = new DefaultExecutorFactory();
        return executorFactory;
    }

    protected InetAddressFactory createInetAddressFactory() {
        return new InetAddressFactory();
    }

    protected OutgoingConnector<Message> createOutgoingConnector() {
        return new TcpOutgoingConnector<Message>(
                new DefaultMessageSerializer<Message>(
                        messageClassLoader));
    }

    protected IncomingConnector<Message> createIncomingConnector() {
        incomingLegacyConnector = new TcpIncomingConnector<Message>(
                get(ExecutorFactory.class),
                new DefaultMessageSerializer<Message>(
                        messageClassLoader),
                get(InetAddressFactory.class),
                idGenerator);
        return incomingLegacyConnector;
    }

    protected OutgoingConnector<InterHubMessage> createOutgoingHubConnector() {
        return new TcpOutgoingConnector<InterHubMessage>(
                new InterHubMessageSerializer(
                        new TypeSafeSerializer<MethodInvocation>(
                                MethodInvocation.class,
                                new MethodInvocationSerializer(
                                        messageClassLoader,
                                        new JavaSerializer<Object[]>(
                                                messageClassLoader)))));
    }

    protected IncomingConnector<InterHubMessage> createIncomingHubConnector() {
        incomingHubConnector = new TcpIncomingConnector<InterHubMessage>(
                get(ExecutorFactory.class),
                new InterHubMessageSerializer(
                        new TypeSafeSerializer<MethodInvocation>(
                                MethodInvocation.class,
                                new MethodInvocationSerializer(
                                        messageClassLoader,
                                        new JavaSerializer<Object[]>(
                                                messageClassLoader)))),
                get(InetAddressFactory.class),
                idGenerator
        );
        return incomingHubConnector;
    }

    protected MessagingClient createMessagingClient() {
        messagingClient = new MessageHubBackedClient(
                createOutgoingHubConnector(),
                get(ExecutorFactory.class));
        return messagingClient;
    }

    protected MessagingServer createMessagingServer() {
        messagingServer = new MessageHubBackedServer(
                createIncomingHubConnector(),
                get(ExecutorFactory.class));
        return messagingServer;
    }

    protected IncomingBroadcast createIncomingBroadcast() {
        incomingBroadcast = new DefaultIncomingBroadcast(
                get(MessageOriginator.class),
                broadcastGroup,
                get(AsyncConnection.class),
                createIncomingConnector(),
                get(ExecutorFactory.class),
                idGenerator,
                messageClassLoader);
        return incomingBroadcast;
    }

    protected OutgoingBroadcast createOutgoingBroadcast() {
        outgoingBroadcast = new DefaultOutgoingBroadcast(
                get(MessageOriginator.class),
                broadcastGroup,
                get(AsyncConnection.class),
                createOutgoingConnector(),
                get(ExecutorFactory.class),
                idGenerator,
                messageClassLoader);
        return outgoingBroadcast;
    }

    protected AsyncConnection<DiscoveryMessage> createMulticastConnection() {
        MulticastConnection<DiscoveryMessage> connection = new MulticastConnection<DiscoveryMessage>(broadcastAddress, new DiscoveryProtocolSerializer());
        multicastConnection = new AsyncConnectionAdapter<DiscoveryMessage>(
                connection,
                new DiscardingFailureHandler<DiscoveryMessage>(LoggerFactory.getLogger(MulticastConnection.class)),
                get(ExecutorFactory.class));
        return multicastConnection;
    }
}
