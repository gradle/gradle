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

import org.gradle.messaging.remote.internal.protocol.ChannelAvailable;
import org.gradle.messaging.remote.internal.protocol.ChannelUnavailable;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.gradle.messaging.remote.internal.protocol.LookupRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ChannelRegistrationProtocol implements Protocol<DiscoveryMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelRegistrationProtocol.class);
    private final Map<String, ChannelAvailable> channels = new HashMap<String, ChannelAvailable>();
    private ProtocolContext<DiscoveryMessage> context;

    public void start(ProtocolContext<DiscoveryMessage> context) {
        this.context = context;
    }

    public void handleIncoming(DiscoveryMessage message) {
        if (message instanceof LookupRequest) {
            handleLookup((LookupRequest) message);
        } else if (!(message instanceof ChannelAvailable) && !(message instanceof ChannelUnavailable)) {
            // Discard
            LOGGER.debug("Received unexpected discovery message {}. Discarding.", message);
        }
        // Else, ignore
    }

    public void handleOutgoing(DiscoveryMessage message) {
        if (message instanceof ChannelAvailable) {
            ChannelAvailable channelAvailable = (ChannelAvailable) message;
            channels.put(channelAvailable.getChannel(), channelAvailable);
            LOGGER.debug("Channel registered. Broadcasting {}.", message);
            context.dispatchOutgoing(message);
        } else if (message instanceof ChannelUnavailable) {
            ChannelUnavailable channelUnavailable = (ChannelUnavailable) message;
            channels.remove(channelUnavailable.getChannel());
            LOGGER.debug("Channel unregistered. Broadcasting {}.", message);
            context.dispatchOutgoing(message);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private void handleLookup(LookupRequest request) {
        ChannelAvailable response = channels.get(request.getChannel());
        if (response != null) {
            LOGGER.debug("Received lookup request {} for known channel. Replying with {}.", request, response);
            context.dispatchOutgoing(response);
        } else {
            LOGGER.debug("Received lookup request {} for unknown channel. Discarding.", request);
        }
    }

    public void stopRequested() {
        try {
            for (ChannelAvailable channelAvailable : channels.values()) {
                context.dispatchOutgoing(new ChannelUnavailable(channelAvailable.getGroup(), channelAvailable.getChannel(), channelAvailable.getAddress()));
            }
        } finally {
            channels.clear();
            context.stopped();
        }
    }
}
