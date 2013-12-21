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

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelIdentifier;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelMessage;
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;
import org.gradle.messaging.serialize.kryo.KryoAwareSerializer;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class InterHubMessageSerializer implements MessageSerializer<InterHubMessage> {
    private final KryoAwareSerializer<Object> payloadSerializer;

    public InterHubMessageSerializer(KryoAwareSerializer<Object> payloadSerializer) {
        this.payloadSerializer = payloadSerializer;
    }

    public ObjectReader<InterHubMessage> newReader(InputStream inputStream, Address localAddress, Address remoteAddress) {
        Input input = new Input(inputStream);
        return new MessageReader(input, payloadSerializer.newReader(input));
    }

    public ObjectWriter<InterHubMessage> newWriter(OutputStream outputStream) {
        Output output = new Output(outputStream);
        return new MessageWriter(output, payloadSerializer.newWriter(output));
    }

    private static class MessageReader implements ObjectReader<InterHubMessage> {
        private final Map<Integer, ChannelIdentifier> channels = new HashMap<Integer, ChannelIdentifier>();
        private final Input input;
        private final ObjectReader<?> payloadReader;

        public MessageReader(Input input, ObjectReader<?> payloadReader) {
            this.input = input;
            this.payloadReader = payloadReader;
        }

        public InterHubMessage read() throws Exception {
            switch (input.readByte()) {
                case 1:
                    ChannelIdentifier channelId = readChannelId();
                    Object payload = payloadReader.read();
                    return new ChannelMessage(channelId, payload);
                case 2:
                    return new EndOfStream();
                default:
                    throw new IllegalArgumentException();
            }
        }

        private ChannelIdentifier readChannelId() {
            int channelNum = input.readInt(true);
            ChannelIdentifier channelId = channels.get(channelNum);
            if (channelId == null) {
                String channel = input.readString();
                channelId = new ChannelIdentifier(channel);
                channels.put(channelNum, channelId);
            }
            return channelId;
        }
    }

    private static class MessageWriter implements ObjectWriter<InterHubMessage> {
        private final Map<ChannelIdentifier, Integer> channels = new HashMap<ChannelIdentifier, Integer>();
        private final Output output;
        private final ObjectWriter<Object> payloadWriter;

        public MessageWriter(Output output, ObjectWriter<Object> payloadWriter) {
            this.output = output;
            this.payloadWriter = payloadWriter;
        }

        public void write(InterHubMessage message) throws Exception {
            if (message instanceof ChannelMessage) {
                ChannelMessage channelMessage = (ChannelMessage) message;
                output.writeByte(1);
                writeChannelId(channelMessage);
                payloadWriter.write(channelMessage.getPayload());
            } else if (message instanceof EndOfStream) {
                output.writeByte(2);
            } else {
                throw new IllegalArgumentException();
            }
            output.flush();
        }

        private void writeChannelId(ChannelMessage channelMessage) {
            Integer channelNum = channels.get(channelMessage.getChannel());
            if (channelNum == null) {
                channelNum = channels.size();
                channels.put(channelMessage.getChannel(), channelNum);
                output.writeInt(channelNum, true);
                output.writeString(channelMessage.getChannel().getName());
            } else {
                output.writeInt(channelNum, true);
            }
        }
    }
}
