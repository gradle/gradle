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
