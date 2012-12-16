package org.gradle.messaging.remote.internal.hub.protocol;

public class EndOfStream extends InterHubMessage {
    @Override
    public boolean isBroadcast() {
        return true;
    }
}
