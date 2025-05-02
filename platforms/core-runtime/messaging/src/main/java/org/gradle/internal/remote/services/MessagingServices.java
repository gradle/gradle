/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.remote.services;

import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.internal.remote.MessagingClient;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.remote.internal.IncomingConnector;
import org.gradle.internal.remote.internal.OutgoingConnector;
import org.gradle.internal.remote.internal.hub.MessageHubBackedClient;
import org.gradle.internal.remote.internal.hub.MessageHubBackedServer;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.remote.internal.inet.TcpIncomingConnector;
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;

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
 * </ul>
 */
public class MessagingServices implements ServiceRegistrationProvider {
    private final IdGenerator<UUID> idGenerator = new UUIDGenerator();

    @Provides
    protected InetAddressFactory createInetAddressFactory() {
        return new InetAddressFactory();
    }

    @Provides
    protected OutgoingConnector createOutgoingConnector() {
        return new TcpOutgoingConnector();
    }

    @Provides
    protected IncomingConnector createIncomingConnector(ExecutorFactory executorFactory, InetAddressFactory inetAddressFactory) {
        return new TcpIncomingConnector(
                executorFactory,
                inetAddressFactory,
                idGenerator
        );
    }

    @Provides
    protected MessagingClient createMessagingClient(OutgoingConnector outgoingConnector, ExecutorFactory executorFactory) {
        return new MessageHubBackedClient(
                outgoingConnector,
                executorFactory);
    }

    @Provides
    protected MessagingServer createMessagingServer(IncomingConnector incomingConnector, ExecutorFactory executorFactory) {
        return new MessageHubBackedServer(
                incomingConnector,
                executorFactory);
    }
}
