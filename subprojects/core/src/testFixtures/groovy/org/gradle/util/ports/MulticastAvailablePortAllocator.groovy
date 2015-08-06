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

import org.gradle.messaging.remote.internal.inet.SocketInetAddress


class MulticastAvailablePortAllocator extends PeerAwareAvailablePortAllocator {
    private final static INSTANCE = new MulticastAvailablePortAllocator()
    final MulticastPortReservationService portReservationService

    private MulticastAvailablePortAllocator() {
        this(new SocketInetAddress(InetAddress.getByName("233.253.17.122"), 7915))
    }

    private MulticastAvailablePortAllocator(SocketInetAddress address) {
        super()
        portReservationService = new MulticastPortReservationService(this, address)
        portReservationService.start()
    }

    public static MulticastAvailablePortAllocator getInstance() {
        return INSTANCE
    }

    @Override
    protected ReservedPortRange reservePortRange() {
        ReservedPortRange range = super.reservePortRange()
        portReservationService.reservePorts(range.startPort, range.endPort)
    }

    @Override
    protected void releaseRange(ReservedPortRange range) {
        super.releaseRange(range)
        portReservationService.releasePorts(range.startPort, range.endPort)
    }
}
