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

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.MessageOriginator;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.inet.InetEndpoint;
import org.gradle.messaging.remote.internal.inet.MultiChoiceAddress;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiscoveryProtocolSerializer implements MessageSerializer<DiscoveryMessage> {
    public static final byte PROTOCOL_VERSION = 2;
    public static final byte LOOKUP_REQUEST = 1;
    public static final byte CHANNEL_AVAILABLE = 2;
    public static final byte CHANNEL_UNAVAILABLE = 3;

    public ObjectReader<DiscoveryMessage> newReader(InputStream inputStream, Address localAddress, Address remoteAddress) {
        return new MessageReader(inputStream, (InetEndpoint) remoteAddress);
    }

    public ObjectWriter<DiscoveryMessage> newWriter(OutputStream outputStream) {
        return new MessageWriter(outputStream);
    }

    private static class MessageReader implements ObjectReader<DiscoveryMessage> {
        private final Input input;
        private final InetEndpoint remoteAddress;

        public MessageReader(InputStream inputStream, InetEndpoint remoteAddress) {
            this.input = new Input(inputStream);
            this.remoteAddress = remoteAddress;
        }

        public DiscoveryMessage read() throws Exception {
            byte protocolVersion = input.readByte();
            if (protocolVersion != PROTOCOL_VERSION) {
                return new UnknownMessage(String.format("unknown protocol version %s", protocolVersion));
            }
            byte messageType = input.readByte();
            switch (messageType) {
                case LOOKUP_REQUEST:
                    return readLookupRequest();
                case CHANNEL_AVAILABLE:
                    return readChannelAvailable();
                case CHANNEL_UNAVAILABLE:
                    return readChannelUnavailable();
            }
            return new UnknownMessage(String.format("unknown message type %s", messageType));
        }

        private DiscoveryMessage readChannelUnavailable() throws IOException {
            MessageOriginator originator = readMessageOriginator();
            String group = input.readString();
            String channel = input.readString();
            Address address = readAddress();
            return new ChannelUnavailable(originator, group, channel, address);
        }

        private DiscoveryMessage readChannelAvailable() throws IOException {
            MessageOriginator originator = readMessageOriginator();
            String group = input.readString();
            String channel = input.readString();
            MultiChoiceAddress address = readAddress();
            address = address.addAddresses(remoteAddress.getCandidates());
            return new ChannelAvailable(originator, group, channel, address);
        }

        private DiscoveryMessage readLookupRequest() throws IOException {
            MessageOriginator originator = readMessageOriginator();
            String group = input.readString();
            String channel = input.readString();
            return new LookupRequest(originator, group, channel);
        }

        private MessageOriginator readMessageOriginator() throws IOException {
            UUID uuid = readUUID();
            String name = input.readString();
            return new MessageOriginator(uuid, name);
        }

        private MultiChoiceAddress readAddress() throws IOException {
            UUID uuid = readUUID();
            int port = input.readInt(true);
            int addressCount = input.readInt(true);
            List<InetAddress> addresses = new ArrayList<InetAddress>();
            for (int i = 0; i < addressCount; i++) {
                int length = input.readInt(true);
                byte[] binAddress = input.readBytes(length);
                InetAddress inetAddress = InetAddress.getByAddress(binAddress);
                addresses.add(inetAddress);
            }
            return new MultiChoiceAddress(uuid, port, addresses);
        }

        private UUID readUUID() throws IOException {
            long mostSigUuidBits = input.readLong();
            long leastSigUuidBits = input.readLong();
            return new UUID(mostSigUuidBits, leastSigUuidBits);
        }
    }

    private static class MessageWriter implements ObjectWriter<DiscoveryMessage> {
        private final Output output;

        public MessageWriter(OutputStream outputStream) {
            this.output = new Output(outputStream);
        }

        public void write(DiscoveryMessage message) throws Exception {
            output.writeByte(PROTOCOL_VERSION);
            if (message instanceof LookupRequest) {
                writeLookupRequest((LookupRequest) message);
            } else if (message instanceof ChannelAvailable) {
                writeChannelAvailable((ChannelAvailable) message);
            } else if (message instanceof ChannelUnavailable) {
                writeChannelUnavailable((ChannelUnavailable) message);
            } else {
                throw new UnsupportedOperationException();
            }
            output.flush();
        }

        private void writeChannelUnavailable(ChannelUnavailable channelUnavailable) throws IOException {
            output.writeByte(CHANNEL_UNAVAILABLE);
            writeMessageOriginator(channelUnavailable.getOriginator());
            output.writeString(channelUnavailable.getGroup());
            output.writeString(channelUnavailable.getChannel());
            writeAddress((MultiChoiceAddress) channelUnavailable.getAddress());
        }

        private void writeChannelAvailable(ChannelAvailable channelAvailable) throws IOException {
            output.writeByte(CHANNEL_AVAILABLE);
            writeMessageOriginator(channelAvailable.getOriginator());
            output.writeString(channelAvailable.getGroup());
            output.writeString(channelAvailable.getChannel());
            writeAddress((MultiChoiceAddress) channelAvailable.getAddress());
        }

        private void writeLookupRequest(LookupRequest request) throws IOException {
            output.writeByte(LOOKUP_REQUEST);
            writeMessageOriginator(request.getOriginator());
            output.writeString(request.getGroup());
            output.writeString(request.getChannel());
        }

        private void writeMessageOriginator(MessageOriginator messageOriginator) throws IOException {
            writeUUID(messageOriginator.getId());
            output.writeString(messageOriginator.getName());
        }

        private void writeAddress(MultiChoiceAddress address) throws IOException {
            writeUUID(address.getCanonicalAddress());
            output.writeInt(address.getPort(), true);
            output.writeInt(address.getCandidates().size(), true);
            for (InetAddress inetAddress : address.getCandidates()) {
                byte[] binAddress = inetAddress.getAddress();
                output.writeInt(binAddress.length, true);
                output.write(binAddress);
            }
        }

        private void writeUUID(Object id) throws IOException {
            UUID uuid = (UUID) id;
            output.writeLong(uuid.getMostSignificantBits());
            output.writeLong(uuid.getLeastSignificantBits());
        }
    }
}
