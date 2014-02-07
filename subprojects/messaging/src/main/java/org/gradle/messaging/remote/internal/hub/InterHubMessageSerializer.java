/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.messaging.remote.internal.hub;

import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelIdentifier;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelMessage;
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.FlushableEncoder;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;
import org.gradle.messaging.serialize.kryo.StatefulSerializer;
import org.gradle.messaging.serialize.kryo.KryoBackedDecoder;
import org.gradle.messaging.serialize.kryo.KryoBackedEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class InterHubMessageSerializer implements MessageSerializer<InterHubMessage> {
    private static final byte CHANNEL_MESSAGE = 1;
    private static final byte END_STREAM_MESSAGE = 2;
    private final StatefulSerializer<Object> payloadSerializer;

    public InterHubMessageSerializer(StatefulSerializer<Object> payloadSerializer) {
        this.payloadSerializer = payloadSerializer;
    }

    public ObjectReader<InterHubMessage> newReader(InputStream inputStream, Address localAddress, Address remoteAddress) {
        Decoder decoder = new KryoBackedDecoder(inputStream);
        return new MessageReader(decoder, payloadSerializer.newReader(decoder));
    }

    public ObjectWriter<InterHubMessage> newWriter(OutputStream outputStream) {
        FlushableEncoder encoder = new KryoBackedEncoder(outputStream);
        return new MessageWriter(encoder, payloadSerializer.newWriter(encoder));
    }

    private static class MessageReader implements ObjectReader<InterHubMessage> {
        private final Map<Integer, ChannelIdentifier> channels = new HashMap<Integer, ChannelIdentifier>();
        private final Decoder decoder;
        private final ObjectReader<?> payloadReader;

        public MessageReader(Decoder decoder, ObjectReader<?> payloadReader) {
            this.decoder = decoder;
            this.payloadReader = payloadReader;
        }

        public InterHubMessage read() throws Exception {
            switch (decoder.readByte()) {
                case CHANNEL_MESSAGE:
                    ChannelIdentifier channelId = readChannelId();
                    Object payload = payloadReader.read();
                    return new ChannelMessage(channelId, payload);
                case END_STREAM_MESSAGE:
                    return new EndOfStream();
                default:
                    throw new IllegalArgumentException();
            }
        }

        private ChannelIdentifier readChannelId() throws IOException {
            int channelNum = decoder.readSmallInt();
            ChannelIdentifier channelId = channels.get(channelNum);
            if (channelId == null) {
                String channel = decoder.readString();
                channelId = new ChannelIdentifier(channel);
                channels.put(channelNum, channelId);
            }
            return channelId;
        }
    }

    private static class MessageWriter implements ObjectWriter<InterHubMessage> {
        private final Map<ChannelIdentifier, Integer> channels = new HashMap<ChannelIdentifier, Integer>();
        private final FlushableEncoder encoder;
        private final ObjectWriter<Object> payloadWriter;

        public MessageWriter(FlushableEncoder encoder, ObjectWriter<Object> payloadWriter) {
            this.encoder = encoder;
            this.payloadWriter = payloadWriter;
        }

        public void write(InterHubMessage message) throws Exception {
            if (message instanceof ChannelMessage) {
                ChannelMessage channelMessage = (ChannelMessage) message;
                encoder.writeByte(CHANNEL_MESSAGE);
                writeChannelId(channelMessage);
                payloadWriter.write(channelMessage.getPayload());
            } else if (message instanceof EndOfStream) {
                encoder.writeByte(END_STREAM_MESSAGE);
            } else {
                throw new IllegalArgumentException();
            }
            encoder.flush();
        }

        private void writeChannelId(ChannelMessage channelMessage) throws IOException {
            Integer channelNum = channels.get(channelMessage.getChannel());
            if (channelNum == null) {
                channelNum = channels.size();
                channels.put(channelMessage.getChannel(), channelNum);
                encoder.writeSmallInt(channelNum);
                encoder.writeString(channelMessage.getChannel().getName());
            } else {
                encoder.writeSmallInt(channelNum);
            }
        }
    }
}
