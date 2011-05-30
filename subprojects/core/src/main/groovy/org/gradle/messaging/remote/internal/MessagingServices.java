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

import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.dispatch.DiscardingFailureHandler;
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.internal.inet.*;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.gradle.messaging.remote.internal.protocol.DiscoveryProtocolSerializer;
import org.gradle.util.UUIDGenerator;
import org.gradle.util.UncheckedException;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
    private final ClassLoader messageClassLoader;
    private final String broadcastGroup;
    private final SocketInetAddress broadcastAddress;
    private final String nodeName;
    private DefaultMessagingClient messagingClient;
    private DefaultMultiChannelConnector multiChannelConnector;
    private TcpIncomingConnector<Message> incomingConnector;
    private DefaultExecutorFactory executorFactory;
    private DefaultMessagingServer messagingServer;
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
        try {
            nodeName = String.format("%s@%s", System.getProperty("user.name"), InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private static SocketInetAddress defaultBroadcastAddress() {
        try {
            return new SocketInetAddress(InetAddress.getByName("233.253.17.122"), 7912);
        } catch (UnknownHostException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    public void stop() {
        close();
    }

    @Override
    public void close() {
        CompositeStoppable stoppable = new CompositeStoppable();
        stoppable.add(incomingConnector);
        stoppable.add(messagingClient);
        stoppable.add(messagingServer);
        stoppable.add(multiChannelConnector);
        stoppable.add(outgoingBroadcast);
        stoppable.add(incomingBroadcast);
        stoppable.add(multicastConnection);
        stoppable.add(executorFactory);
        stoppable.stop();
    }

    protected ExecutorFactory createExecutorFactory() {
        executorFactory = new DefaultExecutorFactory();
        return executorFactory;
    }

    protected OutgoingConnector<Message> createOutgoingConnector() {
        return new TcpOutgoingConnector<Message>(
                new DefaultMessageSerializer<Message>(
                        messageClassLoader));
    }

    protected IncomingConnector<Message> createIncomingConnector() {
        incomingConnector = new TcpIncomingConnector<Message>(
                get(ExecutorFactory.class),
                new DefaultMessageSerializer<Message>(
                        messageClassLoader),
                new InetAddressFactory(),
                new UUIDGenerator());
        return incomingConnector;
    }

    protected MultiChannelConnector createMultiChannelConnector() {
        multiChannelConnector = new DefaultMultiChannelConnector(
                get(OutgoingConnector.class),
                get(IncomingConnector.class),
                get(ExecutorFactory.class),
                messageClassLoader);
        return multiChannelConnector;
    }

    protected MessagingClient createMessagingClient() {
        messagingClient = new DefaultMessagingClient(
                get(MultiChannelConnector.class),
                messageClassLoader);
        return messagingClient;
    }

    protected MessagingServer createMessagingServer() {
        messagingServer = new DefaultMessagingServer(
                get(MultiChannelConnector.class),
                messageClassLoader);
        return messagingServer;
    }

    protected IncomingBroadcast createIncomingBroadcast() {
        incomingBroadcast = new DefaultIncomingBroadcast(
                broadcastGroup,
                nodeName,
                get(AsyncConnection.class),
                get(IncomingConnector.class),
                get(ExecutorFactory.class),
                new UUIDGenerator(),
                messageClassLoader);
        return incomingBroadcast;
    }

    protected OutgoingBroadcast createOutgoingBroadcast() {
        outgoingBroadcast = new DefaultOutgoingBroadcast(
                broadcastGroup,
                nodeName,
                get(AsyncConnection.class),
                get(OutgoingConnector.class),
                get(ExecutorFactory.class),
                new UUIDGenerator(),
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
