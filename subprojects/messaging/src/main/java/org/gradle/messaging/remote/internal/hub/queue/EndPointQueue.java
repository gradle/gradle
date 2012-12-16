package org.gradle.messaging.remote.internal.hub.queue;

import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Condition;

public class EndPointQueue {
    private final List<InterHubMessage> queue = new ArrayList<InterHubMessage>();
    private final MultiEndPointQueue owner;
    private final Condition condition;

    public EndPointQueue(MultiEndPointQueue owner, Condition condition) {
        this.owner = owner;
        this.condition = condition;
    }

    public void put(InterHubMessage message) {
        queue.add(message);
        condition.signalAll();
    }

    public void take(Collection<InterHubMessage> drainTo) {
        if (queue.isEmpty()) {
            owner.empty(this);
        }
        while (queue.isEmpty()) {
            try {
                condition.await();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        drainTo.addAll(queue);
        queue.clear();
    }
}
