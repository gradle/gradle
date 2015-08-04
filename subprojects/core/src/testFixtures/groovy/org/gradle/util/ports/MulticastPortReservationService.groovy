/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.util.ports

import org.gradle.messaging.remote.internal.IncomingBroadcast
import org.gradle.messaging.remote.internal.MessagingServices
import org.gradle.messaging.remote.internal.OutgoingBroadcast
import org.gradle.messaging.remote.internal.inet.SocketInetAddress

/**
 * Coordinates port range reservations across multiple PortAllocator instances on a system.
 * Note that there is currently a race condition where multiple instances could reserve a port range simultaneously,
 * causing those instances to reserve the same range.  Since port range allocation should occur infrequently, and
 * port range selection is random, this should be a low probability scenario.
 */
class MulticastPortReservationService implements PortReservationService {
    final id = UUID.randomUUID()
    final MessagingServices serverMessagingServices
    final MessagingServices clientMessagingServices
    final MulticastPortReservationServer client

    MulticastPortReservationService(PortAllocator portAllocator, SocketInetAddress address) {
        clientMessagingServices = new MessagingServices(getClass().getClassLoader(), "portAllocator", address)
        OutgoingBroadcast outgoingBroadcast = clientMessagingServices.get(OutgoingBroadcast)
        client = outgoingBroadcast.addOutgoing(MulticastPortReservationServer)

        serverMessagingServices = new MessagingServices(getClass().getClassLoader(), "portAllocator", address)
        IncomingBroadcast incomingBroadcast = serverMessagingServices.get(IncomingBroadcast)
        incomingBroadcast.addIncoming(MulticastPortReservationServer, new DefaultMulticastPortRegistrationServer(portAllocator, client, id))
    }

    @Override
    void start() {
        client.hello(id)
    }

    @Override
    void reservePorts(int startPort, int endPort) {
        client.reservePorts(id, startPort, endPort)
    }

    @Override
    void releasePorts(int startPort, int endPort) {
        client.releasePorts(id, startPort, endPort)
    }

    public stop() {
        serverMessagingServices.stop()
        clientMessagingServices.stop()
    }

    static interface MulticastPortReservationServer {
        /**
         * Join the group - existing members should respond by broadcasting their reserved port ranges
         */
        void hello(UUID clientId)
        /**
         * Tell the group about a port range reservation
         */
        void reservePorts(UUID clientId, int startPort, int endPort)
        /**
         * Tell the group about releasing a port range reservation
         */
        void releasePorts(UUID clientId, int startPort, int endPort)
    }

    static class DefaultMulticastPortRegistrationServer implements MulticastPortReservationServer {
        final PortAllocator portAllocator
        final MulticastPortReservationServer client
        final UUID id

        DefaultMulticastPortRegistrationServer(PortAllocator portAllocator, MulticastPortReservationServer client, UUID id) {
            this.portAllocator = portAllocator
            this.client = client
            this.id = id
        }

        @Override
        void hello(UUID clientId) {
            if (! clientId.equals(id)) {
                portAllocator.reservations.each { ReservedPortRange range ->
                    client.reservePorts(id, range.startPort, range.endPort)
                }
            }
        }

        @Override
        void reservePorts(UUID clientId, int startPort, int endPort) {
            if (! clientId.equals(id)) {
                portAllocator.peerReservation(startPort, endPort)
            }
        }

        @Override
        void releasePorts(UUID clientId, int startPort, int endPort) {
            if (! clientId.equals(id)) {
                portAllocator.releasePeerReservation(startPort, endPort)
            }
        }
    }
}
