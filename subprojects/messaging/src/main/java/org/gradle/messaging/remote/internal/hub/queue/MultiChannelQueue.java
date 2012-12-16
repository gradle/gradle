package org.gradle.messaging.remote.internal.hub.queue;

import org.gradle.messaging.remote.internal.hub.protocol.ChannelIdentifier;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelMessage;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

public class MultiChannelQueue {
    private final Lock lock;
    private final Map<ChannelIdentifier, MultiEndPointQueue> channels = new HashMap<ChannelIdentifier, MultiEndPointQueue>();

    public MultiChannelQueue(Lock lock) {
        this.lock = lock;
    }

    public MultiEndPointQueue getChannel(ChannelIdentifier channel) {
        MultiEndPointQueue queue = channels.get(channel);
        if (queue == null) {
            queue = new MultiEndPointQueue(lock);
            channels.put(channel, queue);
        }
        return queue;
    }

    public void queue(InterHubMessage message) {
        if (message instanceof ChannelMessage) {
            ChannelMessage channelMessage = (ChannelMessage) message;
            getChannel(channelMessage.getChannel()).queue(channelMessage);
        } else if (message.isBroadcast()) {
            for (MultiEndPointQueue queue : channels.values()) {
                queue.queue(message);
            }
        } else {
            throw new IllegalArgumentException(String.format("Don't know how to route message %s", message));
        }
    }
}
