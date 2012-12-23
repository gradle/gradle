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
import org.gradle.messaging.remote.internal.MessageOriginator;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.inet.InetEndpoint;
import org.gradle.messaging.remote.internal.inet.MultiChoiceAddress;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;

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

    public ObjectReader<DiscoveryMessage> newReader(DataInputStream inputStream, InetEndpoint localAddress, InetEndpoint remoteAddress) {
        return new MessageReader(inputStream, remoteAddress);
    }

    public ObjectWriter<DiscoveryMessage> newWriter(DataOutputStream outputStream) {
        return new MessageWriter(outputStream);
    }

    private static class MessageReader implements ObjectReader<DiscoveryMessage> {
        private final DataInputStream inputStream;
        private final InetEndpoint remoteAddress;

        public MessageReader(DataInputStream inputStream, InetEndpoint remoteAddress) {
            this.inputStream = inputStream;
            this.remoteAddress = remoteAddress;
        }

        public DiscoveryMessage read() throws Exception {
            byte protocolVersion = inputStream.readByte();
            if (protocolVersion != PROTOCOL_VERSION) {
                return new UnknownMessage(String.format("unknown protocol version %s", protocolVersion));
            }
            byte messageType = inputStream.readByte();
            switch (messageType) {
                case LOOKUP_REQUEST:
                    return readLookupRequest(inputStream);
                case CHANNEL_AVAILABLE:
                    return readChannelAvailable(inputStream, remoteAddress);
                case CHANNEL_UNAVAILABLE:
                    return readChannelUnavailable(inputStream);
            }
            return new UnknownMessage(String.format("unknown message type %s", messageType));
        }

        private DiscoveryMessage readChannelUnavailable(DataInputStream inputStream) throws IOException {
            MessageOriginator originator = readMessageOriginator(inputStream);
            String group = inputStream.readUTF();
            String channel = inputStream.readUTF();
            Address address = readAddress(inputStream);
            return new ChannelUnavailable(originator, group, channel, address);
        }

        private DiscoveryMessage readChannelAvailable(DataInputStream inputStream, InetEndpoint remoteAddress) throws IOException {
            MessageOriginator originator = readMessageOriginator(inputStream);
            String group = inputStream.readUTF();
            String channel = inputStream.readUTF();
            MultiChoiceAddress address = readAddress(inputStream);
            address = address.addAddresses(remoteAddress.getCandidates());
            return new ChannelAvailable(originator, group, channel, address);
        }

        private DiscoveryMessage readLookupRequest(DataInputStream inputStream) throws IOException {
            MessageOriginator originator = readMessageOriginator(inputStream);
            String group = inputStream.readUTF();
            String channel = inputStream.readUTF();
            return new LookupRequest(originator, group, channel);
        }

        private MessageOriginator readMessageOriginator(DataInputStream inputStream) throws IOException {
            UUID uuid = readUUID(inputStream);
            String name = inputStream.readUTF();
            return new MessageOriginator(uuid, name);
        }

        private MultiChoiceAddress readAddress(DataInputStream inputStream) throws IOException {
            UUID uuid = readUUID(inputStream);
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

        private UUID readUUID(DataInputStream inputStream) throws IOException {
            long mostSigUuidBits = inputStream.readLong();
            long leastSigUuidBits = inputStream.readLong();
            return new UUID(mostSigUuidBits, leastSigUuidBits);
        }
    }

    private static class MessageWriter implements ObjectWriter<DiscoveryMessage> {
        private final DataOutputStream outputStream;

        public MessageWriter(DataOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void write(DiscoveryMessage message) throws Exception {
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
            writeMessageOriginator(outputStream, channelUnavailable.getOriginator());
            outputStream.writeUTF(channelUnavailable.getGroup());
            outputStream.writeUTF(channelUnavailable.getChannel());
            writeAddress(outputStream, (MultiChoiceAddress) channelUnavailable.getAddress());
        }

        private void writeChannelAvailable(DataOutputStream outputStream, ChannelAvailable channelAvailable) throws IOException {
            outputStream.writeByte(CHANNEL_AVAILABLE);
            writeMessageOriginator(outputStream, channelAvailable.getOriginator());
            outputStream.writeUTF(channelAvailable.getGroup());
            outputStream.writeUTF(channelAvailable.getChannel());
            writeAddress(outputStream, (MultiChoiceAddress) channelAvailable.getAddress());
        }

        private void writeLookupRequest(DataOutputStream outputStream, LookupRequest request) throws IOException {
            outputStream.writeByte(LOOKUP_REQUEST);
            writeMessageOriginator(outputStream, request.getOriginator());
            outputStream.writeUTF(request.getGroup());
            outputStream.writeUTF(request.getChannel());
        }

        private void writeMessageOriginator(DataOutputStream outputStream, MessageOriginator messageOriginator) throws IOException {
            writeUUID(outputStream, messageOriginator.getId());
            outputStream.writeUTF(messageOriginator.getName());
        }

        private void writeAddress(DataOutputStream outputStream, MultiChoiceAddress address) throws IOException {
            writeUUID(outputStream, address.getCanonicalAddress());
            outputStream.writeInt(address.getPort());
            outputStream.writeInt(address.getCandidates().size());
            for (InetAddress inetAddress : address.getCandidates()) {
                byte[] binAddress = inetAddress.getAddress();
                outputStream.writeInt(binAddress.length);
                outputStream.write(binAddress);
            }
        }

        private void writeUUID(DataOutputStream outputStream, Object id) throws IOException {
            UUID uuid = (UUID) id;
            outputStream.writeLong(uuid.getMostSignificantBits());
            outputStream.writeLong(uuid.getLeastSignificantBits());
        }
    }
}
