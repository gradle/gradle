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

import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Provides some information about the network addresses of the local machine.
 */
public class InetAddressFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(InetAddressFactory.class);
    private final Object lock = new Object();
    private List<InetAddress> localAddresses;
    private List<InetAddress> remoteAddresses;
    private List<NetworkInterface> multicastInterfaces;
    private InetAddress localBindingAddress;

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
                return localAddresses;
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
                return remoteAddresses;
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine the remote IP addresses for this machine.", e);
        }
    }

    /**
     * Locates the network interfaces that should be used for multicast, in order of preference.
     */
    public List<NetworkInterface> findMulticastInterfaces() {
        try {
            synchronized (lock) {
                init();
                return multicastInterfaces;
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine the multicast network interfaces for this machine.", e);
        }
    }

    public InetAddress findLocalBindingAddress() {
        try {
            synchronized (lock) {
                init();
                return localBindingAddress;
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine a usable local IP for this machine.", e);
        }
    }

    private InetAddress findOpenshiftAddresses() {
        for (String key : System.getenv().keySet()) {
            if (key.startsWith("OPENSHIFT_") && key.endsWith("_IP")) {
                String ipAddress = System.getenv(key);
                LOGGER.debug("OPENSHIFT IP environment variable {} detected. Using IP address {}.", key, ipAddress);
                try {
                    return InetAddress.getByName(ipAddress);
                } catch (UnknownHostException e) {
                    throw new RuntimeException(String.format("Unable to use OPENSHIFT IP - invalid IP address '%s' specified in environment variable %s.", ipAddress, key), e);
                }
            }
        }
        return null;
    }

    private void init() throws Exception {
        if (localAddresses != null) {
            return;
        }

        Transformer<Boolean, NetworkInterface> loopback = loopback();
        Transformer<Boolean, NetworkInterface> multicast = multicast();

        localAddresses = new ArrayList<InetAddress>();
        remoteAddresses = new ArrayList<InetAddress>();
        multicastInterfaces = new ArrayList<NetworkInterface>();

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            LOGGER.debug("Adding IP addresses for network interface {}", networkInterface.getDisplayName());
            try {
                Boolean isLoopbackInterface = loopback.transform(networkInterface);
                LOGGER.debug("Is this a loopback interface? {}", isLoopbackInterface);
                Boolean isMulticast = multicast.transform(networkInterface);
                LOGGER.debug("Is this a multicast interface? {}", isMulticast);
                boolean isRemote = false;

                Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
                while (candidates.hasMoreElements()) {
                    InetAddress candidate = candidates.nextElement();
                    if (isLoopbackInterface == null) {
                        // Don't know if this is a loopback interface or not
                        if (candidate.isLoopbackAddress()) {
                            LOGGER.debug("Adding loopback address {}", candidate);
                            localAddresses.add(candidate);
                        } else {
                            LOGGER.debug("Adding remote address {}", candidate);
                            remoteAddresses.add(candidate);
                            isRemote = true;
                        }
                    } else if (isLoopbackInterface) {
                        if (candidate.isLoopbackAddress()) {
                            LOGGER.debug("Adding loopback address {}", candidate);
                            localAddresses.add(candidate);
                        } else {
                            LOGGER.debug("Ignoring remote address on loopback interface {}", candidate);
                        }
                    } else {
                        if (candidate.isLoopbackAddress()) {
                            LOGGER.debug("Ignoring loopback address on remote interface {}", candidate);
                        } else {
                            LOGGER.debug("Adding remote address {}", candidate);
                            remoteAddresses.add(candidate);
                            isRemote = true;
                        }
                    }
                }

                if (!Boolean.FALSE.equals(isMulticast)) {
                    // Prefer remotely reachable interfaces over loopback interfaces for multicast
                    if (isRemote) {
                        LOGGER.debug("Adding remote multicast interface {}", networkInterface.getDisplayName());
                        multicastInterfaces.add(0, networkInterface);
                    } else {
                        LOGGER.debug("Adding loopback multicast interface {}", networkInterface.getDisplayName());
                        multicastInterfaces.add(networkInterface);
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(String.format("Could not determine the IP addresses for network interface %s", networkInterface.getName()), e);
            }
        }

        if (localAddresses.isEmpty()) {
            InetAddress fallback = InetAddress.getByName(null);
            LOGGER.debug("No loopback addresses, using fallback {}", fallback);
            localAddresses.add(fallback);
        }
        if (remoteAddresses.isEmpty()) {
            try {
                InetAddress fallback = InetAddress.getLocalHost();
                LOGGER.debug("No remote addresses, using fallback {}", fallback);
                remoteAddresses.add(fallback);
            } catch (UnknownHostException e) {
                LOGGER.debug("Could not map local host name to remote address, using local addresses instead.");
                remoteAddresses.addAll(localAddresses);
            }
        }
        if (multicastInterfaces.isEmpty()) {
            LOGGER.debug("No multicast interfaces, using fallbacks");
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                multicastInterfaces.add(networkInterfaces.nextElement());
            }
        }

        // Detect Openshift IP environment variable.
        InetAddress openshiftEnvironment = findOpenshiftAddresses();
        if (openshiftEnvironment != null) {
           localBindingAddress = openshiftEnvironment;
        } else {
            localBindingAddress = InetAddress.getByName("0.0.0.0");
        }
    }

    private Transformer<Boolean, NetworkInterface> loopback() {
        try {
            Method method = NetworkInterface.class.getMethod("isLoopback");
            return new MethodBackedTransformer(method);
        } catch (NoSuchMethodException e) {
            return new Unknown();
        }
    }

    private Transformer<Boolean, NetworkInterface> multicast() {
        try {
            Method method = NetworkInterface.class.getMethod("supportsMulticast");
            return new MethodBackedTransformer(method);
        } catch (NoSuchMethodException e) {
            return new Unknown();
        }

    }

    private static class Unknown implements Transformer<Boolean, NetworkInterface> {
        public Boolean transform(NetworkInterface original) {
            return null;
        }
    }

    private static class MethodBackedTransformer implements Transformer<Boolean, NetworkInterface> {
        private final Method method;

        public MethodBackedTransformer(Method method) {
            this.method = method;
        }

        public Boolean transform(NetworkInterface original) {
            try {
                try {
                    return (Boolean) method.invoke(original);
                } catch (InvocationTargetException e) {
                    if (!(e.getCause() instanceof SocketException)) {
                        throw e.getCause();
                    }
                    // Ignore - treat as if we don't know
                    return null;
                }
            } catch (Throwable throwable) {
                throw UncheckedException.throwAsUncheckedException(throwable);
            }
        }
    }
}
