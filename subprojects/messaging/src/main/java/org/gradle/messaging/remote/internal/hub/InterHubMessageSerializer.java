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

import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelIdentifier;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelMessage;
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.remote.internal.inet.InetEndpoint;
import org.gradle.messaging.serialize.ObjectReader;
import org.gradle.messaging.serialize.ObjectWriter;

import java.io.DataInputStream;
import java.io.DataOutputStream;

class InterHubMessageSerializer implements MessageSerializer<InterHubMessage> {
    public ObjectReader<InterHubMessage> newReader(DataInputStream inputStream, InetEndpoint localAddress, InetEndpoint remoteAddress) {
        return new MessageReader(inputStream);
    }

    public ObjectWriter<InterHubMessage> newWriter(DataOutputStream outputStream) {
        return new MessageWriter(outputStream);
    }

    private static class MessageReader implements ObjectReader<InterHubMessage> {
        private final DataInputStream inputStream;

        private MessageReader(DataInputStream inputStream) {
            this.inputStream = inputStream;
        }

        public InterHubMessage read() throws Exception {
            switch (inputStream.readByte()) {
                case 1:
                    String channel = inputStream.readUTF();
                    String payload = inputStream.readUTF();
                    return new ChannelMessage(new ChannelIdentifier(channel), payload);
                case 2:
                    return new EndOfStream();
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    private static class MessageWriter implements ObjectWriter<InterHubMessage> {
        private final DataOutputStream outputStream;

        public MessageWriter(DataOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void write(InterHubMessage message) throws Exception {
            if (message instanceof ChannelMessage) {
                ChannelMessage channelMessage = (ChannelMessage) message;
                outputStream.writeByte(1);
                outputStream.writeUTF(channelMessage.getChannel().getName());
                outputStream.writeUTF(channelMessage.getPayload().toString());
            } else if (message instanceof EndOfStream) {
                outputStream.writeByte(2);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }
}
