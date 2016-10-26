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

package org.gradle.internal.remote.internal.inet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Provides some information about the network addresses of the local machine.
 */
class InetAddresses {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private List<InetAddress> loopback = new ArrayList<InetAddress>();
    private List<InetAddress> remote = new ArrayList<InetAddress>();
    private List<NetworkInterface> multicastInterfaces = new ArrayList<NetworkInterface>();

    InetAddresses() throws SocketException {
        analyzeNetworkInterfaces();

        if (multicastInterfaces.isEmpty()) {
            useMulticastFallback();
        }
    }

    private void analyzeNetworkInterfaces() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            analyzeNetworkInterface(interfaces.nextElement());
        }
    }

    private void analyzeNetworkInterface(NetworkInterface networkInterface) {
        logger.debug("Adding IP addresses for network interface {}", networkInterface.getDisplayName());
        try {
            boolean isLoopbackInterface = networkInterface.isLoopback();
            logger.debug("Is this a loopback interface? {}", isLoopbackInterface);
            boolean isMulticast = networkInterface.supportsMulticast();
            logger.debug("Is this a multicast interface? {}", isMulticast);
            boolean isRemote = false;

            Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
            while (candidates.hasMoreElements()) {
                InetAddress candidate = candidates.nextElement();
                if (isLoopbackInterface) {
                    if (candidate.isLoopbackAddress()) {
                        logger.debug("Adding loopback address {}", candidate);
                        loopback.add(candidate);
                    } else {
                        logger.debug("Ignoring remote address on loopback interface {}", candidate);
                    }
                } else {
                    if (candidate.isLoopbackAddress()) {
                        logger.debug("Ignoring loopback address on remote interface {}", candidate);
                    } else {
                        logger.debug("Adding remote address {}", candidate);
                        remote.add(candidate);
                        isRemote = true;
                    }
                }
            }

            if (isMulticast) {
                // Prefer remotely reachable interfaces over loopback interfaces for multicast
                if (isRemote) {
                    logger.debug("Adding remote multicast interface {}", networkInterface.getDisplayName());
                    multicastInterfaces.add(0, networkInterface);
                } else {
                    logger.debug("Adding loopback multicast interface {}", networkInterface.getDisplayName());
                    multicastInterfaces.add(networkInterface);
                }
            }
        } catch (SocketException e) {
            // Log the error but analyze the remaining interfaces. We could for example run into https://bugs.openjdk.java.net/browse/JDK-7032558
            logger.warn("Error while querying interface {} for IP addresses", networkInterface, e);
        } catch (Throwable e) {
            throw new RuntimeException(String.format("Could not determine the IP addresses for network interface %s", networkInterface.getName()), e);
        }
    }

    private void useMulticastFallback() throws SocketException {
        logger.debug("No multicast interfaces, using fallbacks");
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            multicastInterfaces.add(networkInterfaces.nextElement());
        }
    }

    public List<InetAddress> getLoopback() {
        return loopback;
    }

    public List<InetAddress> getRemote() {
        return remote;
    }

    /**
     * Locates the network interfaces that should be used for multicast, in order of preference.
     */
    public List<NetworkInterface> getMulticastInterfaces() {
        return multicastInterfaces;
    }
}
