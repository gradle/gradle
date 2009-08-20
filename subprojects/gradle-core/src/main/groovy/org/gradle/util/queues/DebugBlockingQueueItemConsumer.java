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
