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
package org.gradle.api.testing.execution.control.client;

import org.gradle.api.testing.execution.control.messages.TestControlMessage;
import org.gradle.util.queues.AbstractBlockingQueueItemConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Tom Eyckmans
 */
public class TestControlMessageQueueConsumer extends AbstractBlockingQueueItemConsumer<TestControlMessage> {
    private static final Logger logger = LoggerFactory.getLogger(TestControlMessageQueueConsumer.class);
    private final TestControlMessageDispatcher dispatcher;

    public TestControlMessageQueueConsumer(BlockingQueue<TestControlMessage> toConsumeQueue, long pollTimeout, TimeUnit pollTimeoutTimeUnit, TestControlMessageDispatcher dispatcher) {
        super(toConsumeQueue, pollTimeout, pollTimeoutTimeUnit);
        this.dispatcher = dispatcher;
    }

    protected boolean consume(TestControlMessage queueItem) {
        try {
            return dispatcher.dispatch(queueItem);
        }
        catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }
}
