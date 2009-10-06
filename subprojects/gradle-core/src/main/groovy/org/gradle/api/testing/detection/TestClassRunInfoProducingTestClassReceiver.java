package org.gradle.api.testing.detection;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.testing.fabric.DefaultTestClassRunInfo;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.util.queues.BlockingQueueItemProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Tom Eyckmans
 */
public class TestClassRunInfoProducingTestClassReceiver implements TestClassReceiver {
    private static final Logger logger = LoggerFactory.getLogger(TestClassRunInfoProducingTestClassReceiver.class);
    private final BlockingQueueItemProducer<TestClassRunInfo> testDetectionQueueProducer;

    private final AtomicLong amountOfReceivedTestClasses = new AtomicLong(0);

    public TestClassRunInfoProducingTestClassReceiver(final BlockingQueue<TestClassRunInfo> testDetectionQueue) {
        testDetectionQueueProducer = new BlockingQueueItemProducer<TestClassRunInfo>(testDetectionQueue, 100L, TimeUnit.MILLISECONDS);
    }

    public void receiveTestClass(String testClassName) {
        if (StringUtils.isNotEmpty(testClassName)) {
            final String javaTestClassName = testClassName
                    .replaceAll("/", ".")
                    .replaceAll("\\.class", "");
            logger.debug("[detection >> pipeline-splitting][{}] test to run {}", amountOfReceivedTestClasses.incrementAndGet(), javaTestClassName);

            testDetectionQueueProducer.produce(new DefaultTestClassRunInfo(javaTestClassName));
        }
    }
}
