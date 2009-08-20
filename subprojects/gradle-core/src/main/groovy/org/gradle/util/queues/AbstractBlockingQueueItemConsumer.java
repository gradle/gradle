/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
