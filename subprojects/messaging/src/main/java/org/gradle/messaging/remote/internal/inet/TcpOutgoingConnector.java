/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.messaging.remote.internal.inet;

import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.ConnectCompletion;
import org.gradle.messaging.remote.internal.ConnectException;
import org.gradle.messaging.remote.internal.OutgoingConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.List;

public class TcpOutgoingConnector implements OutgoingConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpOutgoingConnector.class);

    public ConnectCompletion connect(Address destinationAddress) throws ConnectException {
        if (!(destinationAddress instanceof InetEndpoint)) {
            throw new IllegalArgumentException(String.format("Cannot create a connection to address of unknown type: %s.", destinationAddress));
        }
        InetEndpoint address = (InetEndpoint) destinationAddress;
        LOGGER.debug("Attempting to connect to {}.", address);

        // Try each address in turn. Not all of them are necessarily reachable (eg when socket option IPV6_V6ONLY
        // is on - the default for debian and others), so we will try each of them until we can connect
        List<InetAddress> candidateAddresses = address.getCandidates();

        // Now try each address
        try {
            Exception lastFailure = null;
            for (InetAddress candidate : candidateAddresses) {
                LOGGER.debug("Trying to connect to address {}.", candidate);
                SocketChannel socketChannel;
                try {
                    socketChannel = SocketChannel.open(new InetSocketAddress(candidate, address.getPort()));
                } catch (SocketException e) {
                    LOGGER.debug("Cannot connect to address {}, skipping.", candidate);
                    lastFailure = e;
                    continue;
                }
                LOGGER.debug("Connected to address {}.", socketChannel.socket().getRemoteSocketAddress());
                return new SocketConnectCompletion(socketChannel);
            }
            throw new ConnectException(String.format("Could not connect to server %s. Tried addresses: %s.",
                    destinationAddress, candidateAddresses), lastFailure);
        } catch (ConnectException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not connect to server %s. Tried addresses: %s.",
                    destinationAddress, candidateAddresses), e);
        }
    }
}
