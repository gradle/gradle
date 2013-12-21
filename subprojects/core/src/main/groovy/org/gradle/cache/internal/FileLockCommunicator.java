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

package org.gradle.cache.internal;

import org.gradle.messaging.remote.internal.inet.InetAddressFactory;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class FileLockCommunicator {
    private static final byte PROTOCOL_VERSION = 1;
    private final DatagramSocket socket;
    private final InetAddressFactory addressFactory;
    private boolean stopped;

    public FileLockCommunicator(InetAddressFactory addressFactory) {
        this.addressFactory = addressFactory;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            throw throwAsUncheckedException(e);
        }
    }

    public void pingOwner(int ownerPort, long lockId, String displayName) {
        try {
            byte[] bytesToSend = encode(lockId);
            // Ping the owner via all available local addresses
            for (InetAddress address : addressFactory.findLocalAddresses()) {
                socket.send(new DatagramPacket(bytesToSend, bytesToSend.length, address, ownerPort));
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to ping owner of lock for %s (lock id: %s, port: %s)", displayName, lockId, ownerPort), e);
        }
    }

    public long receive() throws GracefullyStoppedException {
        try {
            byte[] bytes = new byte[9];
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            socket.receive(packet);
            return decode(bytes);
        } catch (IOException e) {
            if (!stopped) {
                throw new RuntimeException(e);
            }
            throw new GracefullyStoppedException();
        }
    }

    public void stop() {
        stopped = true;
        socket.close();
    }

    private static byte[] encode(long lockId) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(packet);
        dataOutput.writeByte(PROTOCOL_VERSION);
        dataOutput.writeLong(lockId);
        dataOutput.flush();
        return packet.toByteArray();
    }

    private static long decode(byte[] bytes) throws IOException {
        DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(bytes));
        byte version = dataInput.readByte();
        if (version != PROTOCOL_VERSION) {
            throw new IllegalArgumentException(String.format("Unexpected protocol version %s received in lock contention notification message", version));
        }
        return dataInput.readLong();
    }

    public int getPort() {
        return socket.getLocalPort();
    }
}