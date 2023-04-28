/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.cache.internal.locklistener

class FileLockUDPCommunicatorTest extends AbstractFileLockCommunicatorTest {

    def setup() {
        communicator =  new FileLockUDPCommunicator(addressFactory)
    }

    @Override
    protected void sendBytes(SocketAddress address, byte[] bytes) {
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        packet.setSocketAddress(address);
        def socket = new DatagramSocket(0, addressFactory.getWildcardBindingAddress())
        socket.send(packet)
    }
}
