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

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class FileLockCommunicator {
    private final DatagramSocket socket;
    private boolean stopped;

    public FileLockCommunicator() {
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            throw throwAsUncheckedException(e);
        }
    }

    public static void pingOwner(int ownerPort, long lockId) {
        DatagramSocket datagramSocket = null;
        try {
            datagramSocket = new DatagramSocket();
            byte[] bytesToSend = encode(lockId);
            datagramSocket.send(new DatagramPacket(bytesToSend, bytesToSend.length, InetAddress.getLocalHost(), ownerPort));
        } catch (IOException e) {
            throw new RuntimeException("Problems pinging owner of lock '" + lockId + "' at port: " + ownerPort);
        } finally {
            if (datagramSocket != null) {
                datagramSocket.close();
            }
        }
    }

    public long receive() throws GracefullyStoppedException {
        try {
            byte[] bytes = new byte[8];
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
        new DataOutputStream(packet).writeLong(lockId);
        return packet.toByteArray();
    }

    private static long decode(byte[] bytes) throws IOException {
        return new DataInputStream(new ByteArrayInputStream(bytes)).readLong();
    }

    public int getPort() {
        return socket.getLocalPort();
    }
}