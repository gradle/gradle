package org.gradle.util.queues;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Tom Eyckmans
 */
public class DebugBlockingQueueItemConsumer <T> extends AbstractBlockingQueueItemConsumer<T> {

    public DebugBlockingQueueItemConsumer(BlockingQueue<T> toConsumeQueue, long pollTimeout, TimeUnit pollTimeoutTimeUnit) {
        super(toConsumeQueue, pollTimeout, pollTimeoutTimeUnit);
    }

    protected boolean consume(T queueItem) {
        System.out.println("[consume] " + queueItem);
        return false;
    }
}
