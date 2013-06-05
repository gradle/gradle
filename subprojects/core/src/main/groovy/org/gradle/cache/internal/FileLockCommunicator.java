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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import static org.gradle.util.GFileUtils.canonicalise;

/**
 * By Szczepan Faber on 5/23/13
 */
public class FileLockCommunicator {
    private DatagramSocket socket;
    private boolean stopped;
    private static final Logger LOG = Logging.getLogger(FileLockCommunicator.class);

    public static void pingOwner(int ownerPort, File target) {
        DatagramSocket datagramSocket = null;
        try {
            datagramSocket = new DatagramSocket();
            byte[] bytesToSend = encodeFile(target);
            datagramSocket.send(new DatagramPacket(bytesToSend, bytesToSend.length, InetAddress.getLocalHost(), ownerPort));
        } catch (IOException e) {
            throw new RuntimeException("Problems pinging owner of '" + target + "' at port: " + ownerPort);
        } finally {
            if (datagramSocket != null) {
                datagramSocket.close();
            }
        }
    }

    public File receive() {
        try {
            byte[] bytes = new byte[2048];
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            socket.receive(packet);
            return decodeFile(bytes);
        } catch (IOException e) {
            if (!stopped) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }

    public void stop() {
        stopped = true;
        if (socket == null) {
            throw new IllegalStateException("The communicator was not started.");
        }
        socket.close();
    }

    private static byte[] encodeFile(File target) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(packet);
        data.writeUTF(target.getAbsolutePath());
        return packet.toByteArray();
    }

    private static File decodeFile(byte[] bytes) throws IOException {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
        return canonicalise(new File(data.readUTF()));
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public void start() {
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isStarted() {
        return socket != null;
    }
}
