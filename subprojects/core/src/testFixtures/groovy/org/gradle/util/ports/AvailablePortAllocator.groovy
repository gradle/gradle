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


class AvailablePortAllocator implements PortAllocator {
    List<ReservedPortRange> reservations = []
    List<ReservedPortRange> peerReservations = []
    private final static INSTANCE = new AvailablePortAllocator()
    private final Lock lock = new ReentrantLock()
    ReservedPortRangeFactory portRangeFactory = new DefaultReservedPortRangeFactory()

    protected AvailablePortAllocator() {
    }

    static AvailablePortAllocator getInstance() {
        return INSTANCE
    }

    @Override
    public int assignPort() {
        try {
            lock.lock()
            return reservePort()
        } finally {
            lock.unlock()
        }
    }

    @Override
    public void releasePort(int port) {
        if (port == null) {
            return
        }

        try {
            lock.lock()
            for (int i = 0; i < reservations.size(); i++) {
                ReservedPortRange range = reservations.get(i)
                if (range.allocated.contains(port)) {
                    range.deallocate(port)
                    if (reservations.size() > 1 && range.allocated.isEmpty()) {
                        releaseRange(range)
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    @Override
    void peerReservation(int startPort, int endPort) {
        try {
            lock.lock()
            ReservedPortRange range = new ReservedPortRange(startPort, endPort)
            if (! peerReservations.contains(range)) {
                peerReservations.add(portRangeFactory.getReservedPortRange(startPort, endPort))
            }
        } finally {
            lock.unlock()
        }
    }

    @Override
    void releasePeerReservation(int startPort, int endPort) {
        try {
            lock.lock()
            peerReservations.remove(new ReservedPortRange(startPort, endPort))
        } finally {
            lock.unlock()
        }
    }

    protected void releaseRange(ReservedPortRange range) {
        reservations.remove(range)
    }

    void clear() {
        reservations.clear()
        peerReservations.clear()
    }

    private int reservePort() {
        while(true) {
            for (int i = 0; i < reservations.size(); i++) {
                ReservedPortRange range = reservations.get(i)
                int port = range.allocate()
                if (port > 0) {
                    return port
                }
            }
            // if we couldn't allocate a port from the existing reserved port ranges, get another range
            reservePortRange()
        }
    }

    protected ReservedPortRange reservePortRange() {
        int rangeCount = (MAX_PRIVATE_PORT - MIN_PRIVATE_PORT) / RANGE_SIZE
        int candidateRange = new Random().nextInt(rangeCount)
        int startRange = candidateRange
        int startPort
        int endPort
        while(true) {
            candidateRange++
            if (candidateRange >= rangeCount) {
                candidateRange = 0
            }

            startPort = MIN_PRIVATE_PORT + (candidateRange * RANGE_SIZE)
            endPort = startPort + RANGE_SIZE - 1
            // if this is the last range in the total list of ports, make the range whatever ports are remaining
            if (endPort > MAX_PRIVATE_PORT) {
                endPort = MAX_PRIVATE_PORT
            }

            if (!(isReserved(startPort, endPort) || isPeerReserved(startPort, endPort))) {
                break
            } else {
                if (candidateRange == startRange) {
                    throw new NoSuchElementException("Unable to find a port range to reserve")
                }
            }
        }
        ReservedPortRange range = portRangeFactory.getReservedPortRange(startPort, endPort)
        reservations.add(range)
        return range
    }

    private boolean isReserved(int startPort, int endPort) {
        return isReservedInList(reservations, startPort, endPort)
    }

    private boolean isPeerReserved(int startPort, int endPort) {
        return isReservedInList(peerReservations, startPort, endPort)
    }

    private static boolean isReservedInList(List<ReservedPortRange> reservationList, int startPort, int endPort) {
        for (int i=0; i<reservationList.size(); i++) {
            ReservedPortRange range = reservationList.get(i)
            if ((startPort <= range.endPort && startPort >= range.startPort)
                || (endPort >= range.startPort && endPort <= range.endPort)) {
                return true
            }
        }
        return false
    }
}
