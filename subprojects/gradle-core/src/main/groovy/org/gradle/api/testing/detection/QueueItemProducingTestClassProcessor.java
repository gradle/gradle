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

import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.fabric.TestClassRunInfoFactory;
import org.gradle.util.queues.BlockingQueueItemProducer;

/**
 * The queue item producing test class processor is used when running tests natively to queue the test class on the
 * test detection queue.
 *
 * @author Tom Eyckmans
 */
public class QueueItemProducingTestClassProcessor implements TestClassProcessor
{
    private final BlockingQueueItemProducer<TestClassRunInfo> testDetectionQueueProducer;
    private final TestClassRunInfoFactory testClassRunInfoFactory;

    public QueueItemProducingTestClassProcessor(
            final BlockingQueueItemProducer<TestClassRunInfo> testDetectionQueueProducer,
            final TestClassRunInfoFactory testClassRunInfoFactory)
    {
        if ( testDetectionQueueProducer == null ) throw new IllegalArgumentException("testDetectionQueueProducer  == null!");
        if ( testClassRunInfoFactory == null ) throw new IllegalArgumentException("testClassRunInfoFactory  == null!");
        
        this.testDetectionQueueProducer = testDetectionQueueProducer;
        this.testClassRunInfoFactory = testClassRunInfoFactory;
    }

    public void processTestClass(final String testClassName) {
        final TestClassRunInfo testClassRunInfo = testClassRunInfoFactory.createTestClassRunInfo(testClassName);

        if ( testClassRunInfo != null ) {
            testDetectionQueueProducer.produce(testClassRunInfo);
        }
    }
}
