package org.gradle.util.queues;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Tom Eyckmans
 */
public class BlockingQueueItemProducer<T> {
    private final BlockingQueue<T> produceToQueue;
    private final long offerTimeout;
    private final TimeUnit offerTimeoutTimeUnit;

    private final AtomicBoolean keepProducing = new AtomicBoolean(true);

    public BlockingQueueItemProducer(BlockingQueue<T> produceToQueue, long offerTimeout, TimeUnit offerTimeoutTimeUnit) {
        if ( produceToQueue == null ) throw new IllegalArgumentException("produceToQueue == null!");
        if ( offerTimeout < 0 ) throw new IllegalArgumentException("offerTimeout < 0!");
        if ( offerTimeoutTimeUnit == null ) throw new IllegalArgumentException("offerTimeoutTimeUnit == null!");
        this.produceToQueue = produceToQueue;
        this.offerTimeout = offerTimeout;
        this.offerTimeoutTimeUnit = offerTimeoutTimeUnit;
    }

    public void stopProducing() {
        keepProducing.set(false);
    }

    public void produce(T queueItem) {
        if ( queueItem == null ) throw new IllegalArgumentException("queueItem == null!");

        boolean itemQueued = false;
        while ( !keepProducing.get() || !itemQueued ) {
            try {
                itemQueued = produceToQueue.offer(queueItem, offerTimeout, offerTimeoutTimeUnit);
            }
            catch ( InterruptedException e ) {
                // ignore - not acceptable - item must be queued
            }

            if ( !itemQueued && keepProducing.get() )
                Thread.yield();
        }
    }
}
