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
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import org.gradle.internal.Pair

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

abstract class AbstractAvailablePortAllocator implements PortAllocator {
    private final List<ReservedPortRange> reservations = []
    protected final Lock lock = new ReentrantLock()
    @VisibleForTesting
    ReservedPortRangeFactory portRangeFactory = new DefaultReservedPortRangeFactory()

    @VisibleForTesting
    public List<ReservedPortRange> getReservations() {
        return ImmutableList.copyOf(reservations)
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
        if (port == null || port < MIN_PRIVATE_PORT || port > MAX_PRIVATE_PORT) {
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

    private ReservedPortRange reservePortRange() {
        def portRange = getNextPortRange(reservations.size())
        ReservedPortRange range = portRangeFactory.getReservedPortRange(portRange.left, portRange.right)
        reservations.add(range)
        return range
    }

    protected abstract Pair<Integer, Integer> getNextPortRange(int rangeNumber)

    private void releaseRange(ReservedPortRange range) {
        try {
            lock.lock();
            reservations.remove(range)
        } finally {
            lock.unlock();
        }
    }
}
