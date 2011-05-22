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
package org.gradle.messaging.remote.internal.protocol;

import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.inet.SocketInetAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;

public class DiscoveryProtocolSerializer implements MessageSerializer<DiscoveryMessage> {
    public static final byte PROTOCOL_VERSION = 1;
    public static final byte LOOKUP_REQUEST = 1;
    public static final byte CHANNEL_AVAILABLE = 2;
    public static final byte CHANNEL_UNAVAILABLE = 3;

    public DiscoveryMessage read(DataInputStream inputStream, Address localAddress, Address remoteAddress) throws Exception {
        byte protocolVersion = inputStream.readByte();
        if (protocolVersion != PROTOCOL_VERSION) {
            return new UnknownMessage(String.format("unknown protocol version %s", protocolVersion));
        }
        byte messageType = inputStream.readByte();
        switch (messageType) {
            case LOOKUP_REQUEST:
                return readLookupRequest(inputStream);
            case CHANNEL_AVAILABLE:
                return readChannelAvailable(inputStream);
            case CHANNEL_UNAVAILABLE:
                return readChannelUnavailable(inputStream);
        }
        return new UnknownMessage(String.format("unknown message type %s", messageType));
    }

    private DiscoveryMessage readChannelUnavailable(DataInputStream inputStream) throws IOException {
        String group = inputStream.readUTF();
        String channel = inputStream.readUTF();
        SocketInetAddress address = readAddress(inputStream);
        return new ChannelUnavailable(group, channel, address);
    }

    private DiscoveryMessage readChannelAvailable(DataInputStream inputStream) throws IOException {
        String group = inputStream.readUTF();
        String channel = inputStream.readUTF();
        SocketInetAddress address = readAddress(inputStream);
        return new ChannelAvailable(group, channel, address);
    }

    private DiscoveryMessage readLookupRequest(DataInputStream inputStream) throws IOException {
        String group = inputStream.readUTF();
        String channel = inputStream.readUTF();
        return new LookupRequest(group, channel);
    }

    private SocketInetAddress readAddress(DataInputStream inputStream) throws IOException {
        int length = inputStream.readInt();
        byte[] binAddress = new byte[length];
        inputStream.readFully(binAddress);
        InetAddress inetAddress = InetAddress.getByAddress(binAddress);
        int port = inputStream.readInt();
        return new SocketInetAddress(inetAddress, port);
    }

    public void write(DiscoveryMessage message, DataOutputStream outputStream) throws Exception {
        outputStream.writeByte(PROTOCOL_VERSION);
        if (message instanceof LookupRequest) {
            writeLookupRequest(outputStream, (LookupRequest) message);
        } else if (message instanceof ChannelAvailable) {
            writeChannelAvailable(outputStream, (ChannelAvailable) message);
        } else if (message instanceof ChannelUnavailable) {
            writeChannelUnavailable(outputStream, (ChannelUnavailable) message);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private void writeChannelUnavailable(DataOutputStream outputStream, ChannelUnavailable channelUnavailable) throws IOException {
        outputStream.writeByte(CHANNEL_UNAVAILABLE);
        outputStream.writeUTF(channelUnavailable.getGroup());
        outputStream.writeUTF(channelUnavailable.getChannel());
        writeAddress(outputStream, (SocketInetAddress) channelUnavailable.getAddress());
    }

    private void writeChannelAvailable(DataOutputStream outputStream, ChannelAvailable channelAvailable) throws IOException {
        outputStream.writeByte(CHANNEL_AVAILABLE);
        outputStream.writeUTF(channelAvailable.getGroup());
        outputStream.writeUTF(channelAvailable.getChannel());
        writeAddress(outputStream, (SocketInetAddress) channelAvailable.getAddress());
    }

    private void writeLookupRequest(DataOutputStream outputStream, LookupRequest request) throws IOException {
        outputStream.writeByte(LOOKUP_REQUEST);
        outputStream.writeUTF(request.getGroup());
        outputStream.writeUTF(request.getChannel());
    }

    private void writeAddress(DataOutputStream outputStream, SocketInetAddress address) throws IOException {
        byte[] binAddress = address.getAddress().getAddress();
        outputStream.writeInt(binAddress.length);
        outputStream.write(binAddress);
        outputStream.writeInt(address.getPort());
    }
}
