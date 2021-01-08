/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.util.ports

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


class ReservedPortRange {
    final int startPort
    final int endPort
    private final Lock lock = new ReentrantLock()
    PortDetector portDetector = new DefaultPortDetector()
    final List<Integer> allocated = []
    int current

    public ReservedPortRange(int startPort, int endPort) {
        this.startPort = startPort
        this.endPort = endPort
        current = startPort + new Random().nextInt(endPort - startPort)
    }

    /**
     * Allocate an available port
     *
     * @return the port that was allocated
     */
    public int allocate() {
        try {
            lock.lock()
            return getAvailablePort()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Deallocate the given port
     *
     * @param port
     */
    public void deallocate(int port) {
        try {
            lock.lock()
            allocated.removeAll(port)
        } finally {
            lock.unlock()
        }
    }

    private int getAvailablePort() {
        int first = current
        while (true) {
            current++
            if (current > endPort) {
                current = startPort
            }

            int candidate = current

            if (!(candidate in allocated) && portDetector.isAvailable(candidate)) {
                allocated.add(candidate)
                return candidate
            } else {
                if (current == first) {
                    return -1
                }
            }
        }
    }

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        ReservedPortRange that = (ReservedPortRange) o

        if (endPort != that.endPort) {
            return false
        }
        if (startPort != that.startPort) {
            return false
        }

        return true
    }

    int hashCode() {
        int result
        result = startPort
        result = 31 * result + endPort
        return result
    }

    @Override
    String toString() {
        return "ReservedPortRange[" + startPort + ":" + endPort + "]"
    }
}
