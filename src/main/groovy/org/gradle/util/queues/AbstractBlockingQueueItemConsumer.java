package org.gradle.util.queues;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractBlockingQueueItemConsumer<T> implements Runnable {

    private final BlockingQueue<T> toConsumeQueue;
    private final long pollTimeout;
    private final TimeUnit pollTimeoutTimeUnit;
    private final AtomicBoolean keepConsuming = new AtomicBoolean(true);
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private volatile Throwable endCause = null;

    protected AbstractBlockingQueueItemConsumer(BlockingQueue<T> toConsumeQueue, long pollTimeout, TimeUnit pollTimeoutTimeUnit) {
        if ( toConsumeQueue == null ) throw new IllegalArgumentException("toConsumeQueue == null!");
        if ( pollTimeout < 0 ) throw new IllegalArgumentException("pollTimeout < 0!");
        if ( pollTimeoutTimeUnit == null ) throw new IllegalArgumentException("pollTimeoutTimeUnit == null!");
        this.toConsumeQueue = toConsumeQueue;
        this.pollTimeout = pollTimeout;
        this.pollTimeoutTimeUnit = pollTimeoutTimeUnit;
    }

    public void stopConsuming() {
        keepConsuming.set(false);
    }

    public boolean isEnded() {
        return ended.get();
    }

    public Throwable getEndCause() {
        return endCause;
    }

    public final void run() {
        try {
            setUp();

            boolean stop = false;
            while ( !stop ) {
                try {
                    final T queueItem = toConsumeQueue.poll(pollTimeout, pollTimeoutTimeUnit);

                    if ( queueItem != null ) {
                        stop = consume(queueItem);
                    }
                }
                catch ( InterruptedException e ) {
                    // ignore
                }

                if ( !keepConsuming.get() ) stop = true;
                if ( !stop ) Thread.yield();
            }

            tearDown();
        }
        catch ( Throwable t ) {
            endCause = t;
        }
        finally {
            ended.set(true);
        }
    }

    protected void setUp() throws Exception { }

    protected abstract boolean consume(T queueItem);

    protected void tearDown() throws Exception { }

}
