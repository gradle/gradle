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
package org.gradle.messaging.remote.internal.inet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class InetAddressFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(InetAddressFactory.class);
    private final Object lock = new Object();
    private List<InetAddress> loopbackAddresses;
    private List<InetAddress> nonLoopbackAddresses;

    /**
     * Determines if the given source address is from the local machine.
     */
    public boolean isLocal(InetAddress address) {
        try {
            synchronized (lock) {
                init();
                return loopbackAddresses.contains(address) || nonLoopbackAddresses.contains(address);
            }
        } catch (SocketException e) {
            throw new RuntimeException("Could not determine the IP addresses for this machine.", e);
        }
    }

    /**
     * Locates all local (loopback) addresses for this machine. Never returns an empty list.
     */
    public List<InetAddress> findLocalAddresses() {
        try {
            synchronized (lock) {
                init();
                if (!loopbackAddresses.isEmpty()) {
                    return loopbackAddresses;
                }
                InetAddress fallback = InetAddress.getByName(null);
                LOGGER.debug("No loopback addresses, using fallback {}", fallback);
                return Collections.singletonList(fallback);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine the local IP addresses for this machine.", e);
        }
    }

    /**
     * Locates the remote (non-loopback) addresses for this machine. Never returns an empty list.
     */
    public List<InetAddress> findRemoteAddresses() {
        try {
            synchronized (lock) {
                init();
                if (!nonLoopbackAddresses.isEmpty()) {
                    return nonLoopbackAddresses;
                }
                InetAddress fallback = InetAddress.getLocalHost();
                LOGGER.debug("No remote addresses, using fallback {}", fallback);
                return Collections.singletonList(fallback);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine the remote IP addresses for this machine.", e);
        }
    }

    private void init() throws SocketException {
        if (loopbackAddresses == null) {
            loopbackAddresses = new ArrayList<InetAddress>();
            nonLoopbackAddresses = new ArrayList<InetAddress>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                LOGGER.debug("Adding IP addresses for network interface {}", networkInterface.getName());
                Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
                while (candidates.hasMoreElements()) {
                    InetAddress candidate = candidates.nextElement();
                    if (candidate.isLoopbackAddress()) {
                        LOGGER.debug("Adding loopback address {}", candidate);
                        loopbackAddresses.add(candidate);
                    } else {
                        LOGGER.debug("Adding non-loopback address {}", candidate);
                        nonLoopbackAddresses.add(candidate);
                    }
                }
            }
        }
    }
}
