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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Set;

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

    public InetAddressFactory() {
    }

    @VisibleForTesting
    public InetAddressFactory(InetAddresses inetAddresses) {
        this.inetAddresses = inetAddresses;
    }

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
        wildcardBindingAddress = new InetSocketAddress(0).getAddress();

        if (!findGradleDaemonBindAddress() && !findOpenshiftAddress()) {
            findLocalBindingAddress();
        }
    }

    /**
     * Prefer first loopback address if available, otherwise use the wildcard address.
     */
    private void findLocalBindingAddress() throws SocketException {
        if (inetAddresses == null) { // For testing
            inetAddresses = new InetAddresses();
        }
        if (inetAddresses.getLoopback().isEmpty()) {
            logger.debug("No loopback address for local binding, using fallback {}", wildcardBindingAddress);
            localBindingAddress = wildcardBindingAddress;
        } else {
            localBindingAddress = InetAddress.getLoopbackAddress();
        }
    }

    private boolean findGradleDaemonBindAddress() {
        InetAddress address = resolveEnvBindAddress("GRADLE_DAEMON_BIND_ADDRESS");
        if (address != null) {
            localBindingAddress = address;
            return true;
        }
        return false;
    }

    private boolean findOpenshiftAddress() {
        for (String key : getEnvKeys()) {
            if (key.startsWith("OPENSHIFT_") && key.endsWith("_IP")) {
                InetAddress address = resolveEnvBindAddress(key);
                if (address != null) {
                    localBindingAddress = address;
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private InetAddress resolveEnvBindAddress(String envVarName) {
        String address = getEnv(envVarName);
        if (address == null) {
            return null;
        }
        try {
            logger.debug("Environment variable {} detected. Using bind address {}.", envVarName, address);
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new RuntimeException(String.format("Invalid bind address '%s' specified in environment variable '%s'.", address, envVarName), e);
        }
    }

    @Nullable
    @VisibleForTesting
    String getEnv(String name) {
        return System.getenv(name);
    }

    @VisibleForTesting
    Set<String> getEnvKeys() {
        return System.getenv().keySet();
    }
}
