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
package org.gradle.api.testing;

import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.detection.TestDetectionOrchestrator;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.execution.PipelineFactory;
import org.gradle.api.testing.pipelinesplit.TestPipelineSplitOrchestrator;
import org.gradle.api.tasks.testing.NativeTest;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Creates the objects needed by the TestOrchestrator.
 *
 * @author Tom Eyckmans
 */
public class TestOrchestratorFactory {
    private final BlockingQueue<TestClassRunInfo> testDetectionQueue;
    private final ForkControl forkControl;
    private final PipelineFactory pipelineFactory;

    /**
     * Initializes itself by creating a test detection queue, a fork control and a pipeline factory.
     *
     * @param testTask The test task that is run.
     * @param testDetectionQueueSize The queue size that needs to be used for the test detection queue.
     */
    public TestOrchestratorFactory(NativeTest testTask, int testDetectionQueueSize) {
        if ( testTask == null ) throw new IllegalArgumentException("testTask is null!");
        if ( testDetectionQueueSize < 1 ) throw new IllegalArgumentException("testDetectionQueueSize < 1!");

        testDetectionQueue = new ArrayBlockingQueue<TestClassRunInfo>(testDetectionQueueSize);
        forkControl = new ForkControl(testTask.getMaximumNumberOfForks());
        pipelineFactory = new PipelineFactory(testTask);
    }

    /**
     * Creates the test detection orchestrator using the test detection queue.
     *
     * @return The test detection orchestator.
     */
    public TestDetectionOrchestrator createTestDetectionOrchestrator()
    {
        return new TestDetectionOrchestrator(testDetectionQueue);
    }

    /**
     * Creates the pipelines manager using the pipeline factory and the fork control.
     *
     * @return The pipelines manager.
     */
    public PipelinesManager createPipelinesManager()
    {
        return new PipelinesManager(pipelineFactory, forkControl);
    }

    /**
     * Creates the pipeline split orchestrator using the test detection queue.
     *
     * @return The pipeline split orchestrator.
     */
    public TestPipelineSplitOrchestrator createTestPipelineSplitOrchestrator()
    {
        return new TestPipelineSplitOrchestrator(testDetectionQueue);
    }

}
