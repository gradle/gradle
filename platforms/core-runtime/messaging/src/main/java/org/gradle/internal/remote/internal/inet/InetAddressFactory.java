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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Provides information on how two processes on this machine can communicate via IP addresses
 */
@ServiceScope(Scope.Global.class)
public class InetAddressFactory {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Object lock = new Object();
    private InetAddress localBindingAddress;
    private InetAddress wildcardBindingAddress;
    private InetAddresses inetAddresses;
    private boolean initialized;

    /**
     * Determines if the IP address can be used for communication with this machine
     */
    public boolean isCommunicationAddress(InetAddress address) {
        return getLocalBindingAddress().equals(address);
    }

    /**
     * Local communication address for this machine
     */
    public InetAddress getLocalBindingAddress() {
        try {
            synchronized (lock) {
                init();
                return localBindingAddress;
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine a usable local IP for this machine.", e);
        }
    }

    /**
     * Wildcard address for this machine
     */
    public InetAddress getWildcardBindingAddress() {
        try {
            synchronized (lock) {
                init();
                return wildcardBindingAddress;
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine a usable wildcard IP for this machine.", e);
        }
    }

    private void init() throws Exception {
        if (initialized) {
            return;
        }

        initialized = true;
        if (inetAddresses == null) { // For testing
            inetAddresses = new InetAddresses();
        }

        wildcardBindingAddress = new InetSocketAddress(0).getAddress();

        findLocalBindingAddress();
        handleOpenshift();
    }

    /**
     * Prefer first loopback address if available, otherwise use the wildcard address.
     */
    private void findLocalBindingAddress() {
        if (inetAddresses.getLoopback().isEmpty()) {
            logger.debug("No loopback address for local binding, using fallback {}", wildcardBindingAddress);
            localBindingAddress = wildcardBindingAddress;
        } else {
            localBindingAddress = InetAddress.getLoopbackAddress();
        }
    }

    private void handleOpenshift() {
        InetAddress openshiftBindAddress = findOpenshiftAddresses();
        if (openshiftBindAddress != null) {
            localBindingAddress = openshiftBindAddress;
        }
    }

    @Nullable
    private InetAddress findOpenshiftAddresses() {
        for (String key : System.getenv().keySet()) {
            if (key.startsWith("OPENSHIFT_") && key.endsWith("_IP")) {
                String ipAddress = System.getenv(key);
                logger.debug("OPENSHIFT IP environment variable {} detected. Using IP address {}.", key, ipAddress);
                try {
                    return InetAddress.getByName(ipAddress);
                } catch (UnknownHostException e) {
                    throw new RuntimeException(String.format("Unable to use OPENSHIFT IP - invalid IP address '%s' specified in environment variable %s.", ipAddress, key), e);
                }
            }
        }
        return null;
    }
}
