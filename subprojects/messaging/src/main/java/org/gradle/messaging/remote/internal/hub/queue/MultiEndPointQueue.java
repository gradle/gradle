package org.gradle.messaging.remote.internal.hub.queue;

import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;

import java.util.*;
import java.util.concurrent.locks.Lock;

// TODO - use circular buffers to avoid copying
public class MultiEndPointQueue {
    private final Set<EndPointQueue> endpoints = new HashSet<EndPointQueue>();
    private final List<InterHubMessage> queue = new ArrayList<InterHubMessage>();
    private final List<EndPointQueue> waiting = new ArrayList<EndPointQueue>();
    private final Lock lock;

    public MultiEndPointQueue(Lock lock) {
        this.lock = lock;
    }

    public void queue(InterHubMessage message) {
        System.out.println(String.format("=== %s queued: %s", this, message));
        queue.add(message);
        flush();
    }

    public void empty(EndPointQueue endPointQueue) {
        System.out.println(String.format("=== %s waiting: %s", this, endPointQueue));
        waiting.add(endPointQueue);
        flush();
    }

    public void drain(Collection<InterHubMessage> drainTo) {
        drainTo.addAll(queue);
        queue.clear();
    }

    private void flush() {
        // TODO - need to do a better job of routing messages when there are multiple endpoints. This is just going to forward all queued messages to the first
        // waiting endpoint, even if there are multiple waiting to do work
        EndPointQueue selected = waiting.isEmpty() ? null : waiting.get(0);
        while (!queue.isEmpty()) {
            InterHubMessage message = queue.get(0);
            if (message.isBroadcast()) {
                System.out.println(String.format("=== %s broadcasting: %s", this, message));
                for (EndPointQueue endpoint : endpoints) {
                    endpoint.put(message);
                }
                queue.remove(0);
                waiting.clear();
                continue;
            }
            if (selected == null) {
                System.out.println(String.format("=== %s nothing waiting for: %s", this, message));
                break;
            }
            System.out.println(String.format("=== %s unicasting: %s", this, message));
            queue.remove(0);
            waiting.remove(selected);
            selected.put(message);
        }
    }

    public EndPointQueue newEndpoint() {
        EndPointQueue endPointQueue = new EndPointQueue(this, lock.newCondition());
        endpoints.add(endPointQueue);
        return endPointQueue;
    }
}
