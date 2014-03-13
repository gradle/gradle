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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Provides some information about the network addresses of the local machine.
 */
public class InetAddressFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(InetAddressFactory.class);
    private final Object lock = new Object();
    private List<InetAddress> localAddresses;
    private List<InetAddress> remoteAddresses;

    /**
     * Determines the name of the local machine.
     */
    public String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return findRemoteAddresses().get(0).toString();
        }
    }

    /**
     * Determines if the given source address is from the local machine.
     */
    public boolean isLocal(InetAddress address) {
        try {
            synchronized (lock) {
                init();
                return localAddresses.contains(address);
            }
        } catch (Exception e) {
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
                if (!localAddresses.isEmpty()) {
                    return localAddresses;
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
                if (!remoteAddresses.isEmpty()) {
                    return remoteAddresses;
                }
                InetAddress fallback = InetAddress.getLocalHost();
                LOGGER.debug("No remote addresses, using fallback {}", fallback);
                return Collections.singletonList(fallback);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine the remote IP addresses for this machine.", e);
        }
    }

    private void init() throws Exception {
        if (localAddresses != null) {
            return;
        }

        Method loopbackMethod;
        try {
            loopbackMethod = NetworkInterface.class.getMethod("isLoopback");
        } catch (NoSuchMethodException e) {
            loopbackMethod = null;
        }

        localAddresses = new ArrayList<InetAddress>();
        remoteAddresses = new ArrayList<InetAddress>();

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            LOGGER.debug("Adding IP addresses for network interface {}", networkInterface.getName());
            try {
                Boolean isLoopbackInterface;
                try {
                    isLoopbackInterface = loopbackMethod == null ? null : (Boolean) loopbackMethod.invoke(networkInterface);
                } catch (InvocationTargetException e) {
                    if (!(e.getCause() instanceof SocketException)) {
                        throw e.getCause();
                    }
                    // Ignore - treat as if we don't know
                    isLoopbackInterface = null;
                }
                LOGGER.debug("Is this a loopback interface? {}", isLoopbackInterface);

                Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
                while (candidates.hasMoreElements()) {
                    InetAddress candidate = candidates.nextElement();
                    if (isLoopbackInterface == null) {
                        // Don't know if this is a loopback interface or not
                        if (candidate.isLoopbackAddress()) {
                            LOGGER.debug("Adding loopback address {}", candidate);
                            localAddresses.add(candidate);
                        } else {
                            LOGGER.debug("Adding non-loopback address {}", candidate);
                            remoteAddresses.add(candidate);
                        }
                    } else if (isLoopbackInterface) {
                        if (candidate.isLoopbackAddress()) {
                            LOGGER.debug("Adding loopback address {}", candidate);
                            localAddresses.add(candidate);
                        } else {
                            LOGGER.debug("Ignoring non-loopback address on loopback interface {}", candidate);
                        }
                    } else {
                        if (candidate.isLoopbackAddress()) {
                            LOGGER.debug("Ignoring loopback address on non-loopback interface {}", candidate);
                        } else {
                            LOGGER.debug("Adding non-loopback address {}", candidate);
                            remoteAddresses.add(candidate);
                        }
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(String.format("Could not determine the IP addresses for network interface %s", networkInterface.getName()), e);
            }
        }
    }
}
