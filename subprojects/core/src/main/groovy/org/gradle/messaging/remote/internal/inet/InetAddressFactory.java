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

import org.gradle.api.GradleException;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class InetAddressFactory {
    /**
     * Locates the local (loopback) addresses for this machine. Never returns an empty list.
     */
    public List<InetAddress> findLocalAddresses() {
        try {
            List<InetAddress> addresses = new ArrayList<InetAddress>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
                while (candidates.hasMoreElements()) {
                    InetAddress candidate = candidates.nextElement();
                    if (candidate.isLoopbackAddress()) {
                        addresses.add(candidate);
                    }
                }
            }
            if (addresses.isEmpty()) {
                addresses.add(InetAddress.getByName(null));
            }
            return addresses;
        } catch (Exception e) {
            throw new GradleException("Could not determine the local IP addresses for this machine.", e);
        }
    }

    /**
     * Locates the remote (non-loopback) addresses for this machine. Never returns an empty list.
     */
    public List<InetAddress> findRemoteAddresses() {
        try {
            List<InetAddress> addresses = new ArrayList<InetAddress>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
                while (candidates.hasMoreElements()) {
                    InetAddress candidate = candidates.nextElement();
                    if (!candidate.isLoopbackAddress()) {
                        addresses.add(candidate);
                    }
                }
            }
            if (addresses.isEmpty()) {
                addresses.add(InetAddress.getLocalHost());
            }
            return addresses;
        } catch (Exception e) {
            throw new GradleException("Could not determine the remote IP addresses for this machine.", e);
        }
    }
}
