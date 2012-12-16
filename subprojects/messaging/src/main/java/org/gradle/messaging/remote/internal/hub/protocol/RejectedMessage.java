package org.gradle.messaging.remote.internal.hub.protocol;

public class RejectedMessage extends InterHubMessage {
    private final ChannelIdentifier channelIdentifier;
    private final Object payload;

    public RejectedMessage(ChannelIdentifier channelIdentifier, Object payload) {
        this.channelIdentifier = channelIdentifier;
        this.payload = payload;
    }

    @Override
    public boolean isBroadcast() {
        return true;
    }

    public ChannelIdentifier getChannelIdentifier() {
        return channelIdentifier;
    }

    public Object getPayload() {
        return payload;
    }
}
