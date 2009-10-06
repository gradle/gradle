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

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.detection.TestDetectionOrchestrator;
import org.gradle.api.testing.execution.PipelineFactory;
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.pipelinesplit.TestPipelineSplitOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Controls high level test execution.
 *
 * @author Tom Eyckmans
 */
public class TestOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(TestOrchestrator.class);

    private final NativeTest testTask;

    private final TestDetectionOrchestrator detectionOrchestrator;
    private final TestPipelineSplitOrchestrator pipelineSplitOrchestrator;
    private final ForkControl forkControl;
    private final PipelineFactory pipelineFactory;
    private final PipelinesManager pipelinesManager;

    public TestOrchestrator(final NativeTest testTask) {
        if (testTask == null) throw new IllegalArgumentException("testTask == null!");
        this.testTask = testTask;

        final BlockingQueue<TestClassRunInfo> testDetectionQueue = new ArrayBlockingQueue<TestClassRunInfo>(1000);

        detectionOrchestrator = new TestDetectionOrchestrator(testTask, testDetectionQueue);

        forkControl = new ForkControl(testTask.getMaximumNumberOfForks());

        pipelineFactory = new PipelineFactory(testTask);

        pipelinesManager = new PipelinesManager(pipelineFactory, forkControl);
        pipelineSplitOrchestrator = new TestPipelineSplitOrchestrator(testDetectionQueue);
    }

    public NativeTest getTestTask() {
        return testTask;
    }

    public void execute() {
        pipelinesManager.initialize(testTask); // initialize the pipelines

        pipelineSplitOrchestrator.startPipelineSplitting(pipelinesManager);
        logger.debug("test - pipeline splitting - started");

        detectionOrchestrator.startDetection();
        logger.debug("test - detection - started");
        detectionOrchestrator.waitForDetectionEnd();
        logger.debug("test - detection - ended");

        pipelineSplitOrchestrator.waitForPipelineSplittingEnded();
        logger.debug("test - pipeline splitting - ended");

        pipelinesManager.pipelineSplittingEnded();
        pipelinesManager.waitForExecutionEnd();
    }
}
