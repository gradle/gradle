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

package org.gradle.cache.internal.locklistener;

import org.gradle.internal.Pair;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Arrays;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class FileLockUDPCommunicator extends AbstractFileLockCommunicator {

    private final DatagramSocket socket;

    public FileLockUDPCommunicator(InetAddressFactory addressFactory) {
        super(addressFactory);
        try {
            socket = new DatagramSocket(0, getBindingAddress());
        } catch (SocketException e) {
            throw throwAsUncheckedException(e);
        }
    }

    @Override
    protected void sendBytes(SocketAddress address, byte[] bytes) throws IOException {
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        packet.setSocketAddress(address);
        socket.send(packet);
    }

    @Override
    protected Pair<byte[], InetSocketAddress> receiveBytes() throws IOException {
        byte[] bytes = new byte[FileLockPacketPayload.MAX_BYTES];
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);
        return Pair.of(Arrays.copyOfRange(packet.getData(), 0, packet.getLength()), (InetSocketAddress) packet.getSocketAddress());
    }

    @Override
    public void stop() {
        super.stop();
        socket.close();
    }

    @Override
    public int getPort() {
        return socket.getLocalPort();
    }
}
