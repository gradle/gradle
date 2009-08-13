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
