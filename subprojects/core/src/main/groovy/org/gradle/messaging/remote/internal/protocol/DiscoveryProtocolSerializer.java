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
import org.gradle.messaging.remote.internal.inet.MultiChoiceAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        Address address = readAddress(inputStream);
        return new ChannelUnavailable(group, channel, address);
    }

    private DiscoveryMessage readChannelAvailable(DataInputStream inputStream) throws IOException {
        String group = inputStream.readUTF();
        String channel = inputStream.readUTF();
        Address address = readAddress(inputStream);
        return new ChannelAvailable(group, channel, address);
    }

    private DiscoveryMessage readLookupRequest(DataInputStream inputStream) throws IOException {
        String group = inputStream.readUTF();
        String channel = inputStream.readUTF();
        return new LookupRequest(group, channel);
    }

    private MultiChoiceAddress readAddress(DataInputStream inputStream) throws IOException {
        long mostSigUuidBits = inputStream.readLong();
        long leastSigUuidBits = inputStream.readLong();
        UUID uuid = new UUID(mostSigUuidBits, leastSigUuidBits);
        int port = inputStream.readInt();
        int addressCount = inputStream.readInt();
        List<InetAddress> addresses = new ArrayList<InetAddress>();
        for (int i = 0; i < addressCount; i++) {
            int length = inputStream.readInt();
            byte[] binAddress = new byte[length];
            inputStream.readFully(binAddress);
            InetAddress inetAddress = InetAddress.getByAddress(binAddress);
            addresses.add(inetAddress);
        }
        return new MultiChoiceAddress(uuid, port, addresses);
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
        writeAddress(outputStream, (MultiChoiceAddress) channelUnavailable.getAddress());
    }

    private void writeChannelAvailable(DataOutputStream outputStream, ChannelAvailable channelAvailable) throws IOException {
        outputStream.writeByte(CHANNEL_AVAILABLE);
        outputStream.writeUTF(channelAvailable.getGroup());
        outputStream.writeUTF(channelAvailable.getChannel());
        writeAddress(outputStream, (MultiChoiceAddress) channelAvailable.getAddress());
    }

    private void writeLookupRequest(DataOutputStream outputStream, LookupRequest request) throws IOException {
        outputStream.writeByte(LOOKUP_REQUEST);
        outputStream.writeUTF(request.getGroup());
        outputStream.writeUTF(request.getChannel());
    }

    private void writeAddress(DataOutputStream outputStream, MultiChoiceAddress address) throws IOException {
        UUID uuid = (UUID) address.getCanonicalAddress();
        outputStream.writeLong(uuid.getMostSignificantBits());
        outputStream.writeLong(uuid.getLeastSignificantBits());
        outputStream.writeInt(address.getPort());
        outputStream.writeInt(address.getCandidates().size());
        for (InetAddress inetAddress : address.getCandidates()) {
            byte[] binAddress = inetAddress.getAddress();
            outputStream.writeInt(binAddress.length);
            outputStream.write(binAddress);
        }
    }
}
