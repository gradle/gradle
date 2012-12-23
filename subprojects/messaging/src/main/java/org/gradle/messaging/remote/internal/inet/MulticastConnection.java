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
package org.gradle.messaging.remote.internal.inet;

import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.MessageIOException;
import org.gradle.messaging.remote.internal.MessageSerializer;

import java.io.*;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketException;

public class MulticastConnection<T> implements Connection<T> {
    private static final int MAX_MESSAGE_SIZE = 32*1024;
    private final MulticastSocket socket;
    private final SocketInetAddress address;
    private final MessageSerializer<T> serializer;
    private final SocketInetAddress localAddress;

    public MulticastConnection(SocketInetAddress address, MessageSerializer<T> serializer) {
        this.address = address;
        this.serializer = serializer;
        try {
            socket = new MulticastSocket(address.getPort());
            socket.joinGroup(address.getAddress());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        localAddress = new SocketInetAddress(socket.getInetAddress(), socket.getLocalPort());
    }

    @Override
    public String toString() {
        return String.format("multicast connection %s", address);
    }

    public void dispatch(T message) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            serializer.newWriter(outputStream).write(message);
            outputStream.close();
            byte[] buffer = outputStream.toByteArray();
            socket.send(new DatagramPacket(buffer, buffer.length, address.getAddress(), address.getPort()));
        } catch (Exception e) {
            throw new MessageIOException(String.format("Could not write multi-cast message on %s.", address), e);
        }
    }

    public T receive() {
        try {
            byte[] buffer = new byte[MAX_MESSAGE_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, 0, buffer.length);
            socket.receive(packet);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
            return serializer.newReader(inputStream, localAddress, new SocketInetAddress(packet.getAddress(), packet.getPort())).read();
        } catch (SocketException e) {
            // Assume closed
            return null;
        } catch (Exception e) {
            throw new MessageIOException(String.format("Could not receive multi-cast message on %s", address), e);
        }
    }

    public void requestStop() {
        socket.close();
    }

    public void stop() {
        requestStop();
    }
}
