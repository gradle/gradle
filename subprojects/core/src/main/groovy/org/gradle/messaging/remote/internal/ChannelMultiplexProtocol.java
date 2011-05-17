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
package org.gradle.messaging.remote.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * Substitutes channel identifiers with an integer id.
 */
public class ChannelMultiplexProtocol implements Protocol<Object> {
    private final Map<Object, Integer> outgoingChannels = new HashMap<Object, Integer>();
    private final Map<Integer, Object> incomingChannels = new HashMap<Integer, Object>();
    private ProtocolContext<Object> context;

    public void start(ProtocolContext<Object> protocolContext) {
        this.context = protocolContext;
    }

    public void handleIncoming(Object message) {
        if (message instanceof ChannelMetaInfo) {
            ChannelMetaInfo metaInfo = (ChannelMetaInfo) message;
            incomingChannels.put(metaInfo.getChannelId(), metaInfo.getChannelKey());
            return;
        }
        if (message instanceof ChannelMessage) {
            ChannelMessage channelMessage = (ChannelMessage) message;
            context.dispatchIncoming(new ChannelMessage(incomingChannels.get(channelMessage.getChannel()),
                    channelMessage.getPayload()));
            return;
        }

        context.dispatchIncoming(message);
    }

    public void handleOutgoing(Object message) {
        if (message instanceof ChannelMessage) {
            ChannelMessage channelMessage = (ChannelMessage) message;
            Object key = channelMessage.getChannel();
            Integer id = outgoingChannels.get(key);
            if (id == null) {
                id = outgoingChannels.size();
                outgoingChannels.put(key, id);
                context.dispatchOutgoing(new ChannelMetaInfo(key, id));
            }
            context.dispatchOutgoing(new ChannelMessage(id, channelMessage.getPayload()));
        } else {
            context.dispatchOutgoing(message);
        }
    }

    public void stopRequested() {
    }
}
