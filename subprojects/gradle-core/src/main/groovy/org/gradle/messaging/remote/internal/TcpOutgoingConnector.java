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

package org.gradle.messaging.remote.internal;

import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class TcpOutgoingConnector implements OutgoingConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpOutgoingConnector.class);
    private ClassLoader classLoader;

    public TcpOutgoingConnector(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public <T> Connection<T> connect(URI destinationUri) {
        if (!destinationUri.getScheme().equals("tcp") || destinationUri.getHost() == null || !destinationUri.getHost().equals("localhost")) {
            throw new IllegalArgumentException(String.format("Cannot create connection to destination URI '%s'.",
                    destinationUri));
        }

        // Find all loop back addresses. Not all of them are necessarily reachable (eg when socket option IPV6_V6ONLY
        // is on - the default for debian and others), so we will try each of them until we can connect
        List<InetAddress> loopBackAddresses = findLocalAddresses();

        // Now try each address
        try {
            SocketException lastFailure = null;
            for (InetAddress address : loopBackAddresses) {
                LOGGER.debug("Trying to connect to address {}.", address);
                SocketChannel socketChannel;
                try {
                    socketChannel = SocketChannel.open(new InetSocketAddress(address, destinationUri.getPort()));
                } catch (SocketException e) {
                    LOGGER.debug("Cannot connect to address {}, skipping.", address);
                    lastFailure = e;
                    continue;
                }
                LOGGER.debug("Connected to address {}.", address);
                URI localAddress = new URI(String.format("tcp://localhost:%d", socketChannel.socket().getLocalPort()));
                return new SocketConnection<T>(socketChannel, localAddress, destinationUri, classLoader);
            }
            throw lastFailure;
        } catch (java.net.ConnectException e) {
            throw new ConnectException(String.format("Could not connect to server %s. Tried addresses: %s.",
                    destinationUri, loopBackAddresses), e);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not connect to server %s. Tried addresses: %s.",
                    destinationUri, loopBackAddresses), e);
        }
    }

    /**
     * Never returns an empty list.
     */
    static List<InetAddress> findLocalAddresses() {
        try {
            List<InetAddress> addresses = new ArrayList<InetAddress>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
                while (candidates.hasMoreElements()) {
                    InetAddress inetAddress = candidates.nextElement();
                    if (inetAddress.isLoopbackAddress()) {
                        addresses.add(inetAddress);
                    }
                }
            }
            if (addresses.isEmpty()) {
                addresses.add(InetAddress.getByName(null));
            }
            LOGGER.debug("Found loop-back addresses: {}.", addresses);
            return addresses;
        } catch (Exception e) {
            throw new GradleException("Could not determine the local loop-back addresses.", e);
        }
    }
}
