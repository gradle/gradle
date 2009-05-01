package org.gradle.util.queues;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Tom Eyckmans
 */
public class DoNothingBlockingQueueItemConsumer<T> extends AbstractBlockingQueueItemConsumer<T> {
    
    public DoNothingBlockingQueueItemConsumer(BlockingQueue<T> toConsumeQueue, long pollTimeout, TimeUnit pollTimeoutTimeUnit) {
        super(toConsumeQueue, pollTimeout, pollTimeoutTimeUnit);
    }

    protected boolean consume(T queueItem) {
        return false;
    }
}
