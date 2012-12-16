package org.gradle.messaging.remote.internal.hub.protocol;

public class ChannelMessage extends InterHubMessage {
    private final ChannelIdentifier channel;
    private final Object payload;

    public ChannelMessage(ChannelIdentifier channel, Object payload) {
        this.channel = channel;
        this.payload = payload;
    }

    @Override
    public boolean isBroadcast() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("[channel: %s, payload: %s]", channel, payload);
    }

    public ChannelIdentifier getChannel() {
        return channel;
    }

    public Object getPayload() {
        return payload;
    }
}
