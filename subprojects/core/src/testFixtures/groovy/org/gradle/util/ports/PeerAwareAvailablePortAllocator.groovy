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


class PeerAwareAvailablePortAllocator  extends AvailablePortAllocator implements PeerAwarePortAllocator {
    private final static INSTANCE = new PeerAwareAvailablePortAllocator()
    List<ReservedPortRange> peerReservations = []

    protected PeerAwareAvailablePortAllocator() {
        super()
    }

    static PeerAwareAvailablePortAllocator getInstance() {
        return INSTANCE
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

    @Override
    protected boolean isReserved(int startPort, int endPort) {
        return super.isReserved(startPort, endPort) || isPeerReserved(startPort, endPort)
    }

    @Override
    void clear() {
        super.clear()
        peerReservations.clear()
    }

    private boolean isPeerReserved(int startPort, int endPort) {
        return isReservedInList(peerReservations, startPort, endPort)
    }
}
