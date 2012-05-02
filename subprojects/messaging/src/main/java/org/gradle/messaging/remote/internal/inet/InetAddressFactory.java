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
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

public class InetAddressFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(InetAddressFactory.class);
    
    /**
     * Locates the local (loopback) addresses for this machine. Never returns an empty list.
     */
    public List<InetAddress> findLocalAddresses() {
        try {
            LOGGER.debug("Locating local addresses for this machine.");
            List<InetAddress> addresses = new ArrayList<InetAddress>();
            filterIpAddresses(true, addresses);
            if (addresses.isEmpty()) {
                InetAddress fallback = InetAddress.getByName(null);
                LOGGER.debug("No loopback addresses, using fallback {}", fallback);
                addresses.add(fallback);
            }
            return addresses;
        } catch (Exception e) {
            throw new RuntimeException("Could not determine the local IP addresses for this machine.", e);
        }
    }

    /**
     * Locates the remote (non-loopback) addresses for this machine. Never returns an empty list.
     */
    public List<InetAddress> findRemoteAddresses() {
        try {
            LOGGER.debug("Locating remote addresses for this machine.");
            List<InetAddress> addresses = new ArrayList<InetAddress>();
            filterIpAddresses(false, addresses);
            if (addresses.isEmpty()) {
                InetAddress fallback = InetAddress.getLocalHost();
                LOGGER.debug("No remote addresses, using fallback {}", fallback);
                addresses.add(fallback);
            }
            return addresses;
        } catch (Exception e) {
            throw new RuntimeException("Could not determine the remote IP addresses for this machine.", e);
        }
    }

    private void filterIpAddresses(boolean loopback, Collection<InetAddress> addresses) throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            LOGGER.debug("Adding IP addresses for network interface {}", networkInterface.getName());
            Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
            while (candidates.hasMoreElements()) {
                InetAddress candidate = candidates.nextElement();
                if ((loopback && candidate.isLoopbackAddress()) || (!loopback && !candidate.isLoopbackAddress())) {
                    LOGGER.debug("Adding IP address {}", candidate);
                    addresses.add(candidate);
                }
            }
        }
    }
}
