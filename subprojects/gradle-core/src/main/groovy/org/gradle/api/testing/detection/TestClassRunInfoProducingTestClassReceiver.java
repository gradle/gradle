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
