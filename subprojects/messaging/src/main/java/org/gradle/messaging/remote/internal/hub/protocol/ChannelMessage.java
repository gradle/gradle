package org.gradle.messaging.remote.internal.hub.protocol;

public class ChannelMessage extends InterHubMessage {
    private final ChannelIdentifier channel;
    private final Object payload;

    public ChannelMessage(ChannelIdentifier channel, Object payload) {
        this.channel = channel;
        this.payload = payload;
    }

    public ChannelIdentifier getChannel() {
        return channel;
    }

    public Object getPayload() {
        return payload;
    }
}
