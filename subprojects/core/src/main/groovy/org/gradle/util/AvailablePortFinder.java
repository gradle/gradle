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

package org.gradle.util;

import net.jcip.annotations.ThreadSafe;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds currently available server ports within a certain port range.
 * Code originally taken from Apache MINA.
 *
 * <em>Note:</em> If possible, it's preferable to let the party creating the server socket
 * select the port (e.g. with <tt>new ServerSocket(0)</tt>) and then query it for the port
 * chosen. With this class, there is always a risk that someone else grabs the port between
 * the time it is returned from <tt>getNextAvailable()</tt> and the time the socket is created.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @see <a href="http://www.iana.org/assignments/port-numbers">IANA.org</a>
 */
@ThreadSafe
public class AvailablePortFinder {
    public static final int MIN_WELL_KNOWN_PORT = 0;

    public static final int MAX_WELL_KNOWN_PORT = 1023;

    public static final int MIN_REGISTERED_PORT = 1024;

    public static final int MAX_REGISTERED_PORT = 49151;

    public static final int MIN_PRIVATE_PORT = 49152;

    public static final int MAX_PRIVATE_PORT = 65535;

    public static final int MIN_PORT = MIN_WELL_KNOWN_PORT;

    public static final int MAX_PORT = MAX_PRIVATE_PORT;

    private final int fromPort;
    private final int toPort;

    private final AtomicInteger candidateCounter = new AtomicInteger(0);

    /**
     * Creates a port finder that operates on well-known and registered ports.
     *
     * @return a port finder that operates on well-known and registered ports
     */
    public static AvailablePortFinder create() {
        return new AvailablePortFinder(MIN_WELL_KNOWN_PORT, MAX_REGISTERED_PORT);
    }

    /**
     * Creates a port finder that operates on private ports.
     *
     * @return a port finder that operates on private ports
     */
    public static AvailablePortFinder createPrivate() {
        return new AvailablePortFinder(MIN_PRIVATE_PORT, MAX_PRIVATE_PORT);
    }

    /**
     * Creates a port finder that operates within the specified port range.
     *
     * @param fromPort the lower bound of the port range (inclusive)
     * @param toPort the upper bound of the port range (inclusive)
     *
     * @return a port finder that operates within the specified port range
     */
    public static AvailablePortFinder create(int fromPort, int toPort) {
        return new AvailablePortFinder(fromPort, toPort);
    }

    private AvailablePortFinder(int fromPort, int toPort) {
        if (fromPort < MIN_PORT || toPort > MAX_PORT || fromPort > toPort) {
            throw new IllegalArgumentException("Invalid port range");
        }

        this.fromPort = fromPort;
        this.toPort = toPort;
    }

    /**
     * Returns the lower bound of the port range (inclusive).
     *
     * @return the lower bound of the port range (inclusive)
     */
    public int getFromPort() {
        return fromPort;
    }

    /**
     * Returns the upper bound of the port range (inclusive).
     *
     * @return the upper bound of the port range (inclusive)
     */
    public int getToPort() {
        return toPort;
    }

    /**
     * Gets the next available port. Every port in the range is tried at most once.
     * Tries to avoid returning the same port on successive invocations (but it may
     * happen if no other available ports are found).
     *
     * @throws NoSuchElementException if no available port is found
     *
     * @return the next available port
     */
    public int getNextAvailable() {
        int range = toPort - fromPort + 1;
        int curr = candidateCounter.getAndIncrement();
        int last = curr + range;
        while (curr < last) {
            int candidate = fromPort + curr % range;
            if (available(candidate)) {
                return candidate;
            }
            curr = candidateCounter.getAndIncrement();
        }

        throw new NoSuchElementException("Could not find an available port within port range");
    }

    /**
     * Checks to see if a specific port is available.
     *
     * @param port the port to check for availability
     *
     * @return <tt>true</tt> if the port is available, <tt>false</tt> otherwise
     */
    public boolean available(int port) {
        if (port < fromPort || port > toPort) {
            throw new IllegalArgumentException("Port outside port range: " + port);
        }

        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
            /* checkstyle drives me nuts */
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException ignored) {
                    /* checkstyle drives me nuts */
                }
            }
        }

        return false;
    }
}
