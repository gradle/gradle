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
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.pipelinesplit.TestPipelineSplitOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls high level test execution.
 *
 * @author Tom Eyckmans
 */
public class TestOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(TestOrchestrator.class);
    /**
     * The test task that has the configuration that needs to be used by this test orchestrator.
     */
    private final NativeTest testTask;
    private final TestOrchestratorFactory factory;

    /**
     * Initialize a test orchestrator with the test task that needs to be run and the default test orchestrator factory
     * with a default test detection queue size of 1000.
     *
     * @param testTask The test task that needs to be run.
     */
    public TestOrchestrator(final NativeTest testTask) {
        this(testTask, new TestOrchestratorFactory(testTask, 1000));
    }

    /**
     * Constructor used for test purposes to mock out the test orchestrator factory or initialize the default test
     * orchestator factory with a different test detection queue size or with a custom test orchestator factory.
     *
     * @param testTask The test task that needs to be run.
     * @param factory The test orchestator factory to use.
     * @throws IllegalArgumentException when either testTask or factory are null.
     */
    public TestOrchestrator(final NativeTest testTask, TestOrchestratorFactory factory)
    {
        if (testTask == null) throw new IllegalArgumentException("testTask == null!");
        if (factory == null) throw new IllegalArgumentException("factory == null!");

        this.testTask = testTask;
        this.factory = factory;
    }

    /**
     * Initialization:
     *
     * Use the TestOrchestatorFactory to create instances of a test detection orchestrator, test pipeline split
     * orchestrator and a pipelines manager.
     *
     * Then initialize the pipelines manager, the test detection and pipeline splitting start.
     *
     * Execution:
     * When test are detected they advance to pipeline splitting.
     * When pipeline splitting decides on which pipeline to execute the test they advance to that pipeline.
     * When a pipeline receives a test it executes it.
     *
     */
    public void execute() {
        // initialization
        final TestDetectionOrchestrator detectionOrchestrator = factory.createTestDetectionOrchestrator();
        final TestPipelineSplitOrchestrator pipelineSplitOrchestrator = factory.createTestPipelineSplitOrchestrator();
        final PipelinesManager pipelinesManager = factory.createPipelinesManager();

        pipelinesManager.initialize(testTask);

        // execution
        detectionOrchestrator.startDetection(testTask);
        logger.debug("test - detection - started");

        pipelineSplitOrchestrator.startPipelineSplitting(pipelinesManager);
        logger.debug("test - pipeline splitting - started");

        detectionOrchestrator.waitForDetectionEnd();
        logger.debug("test - detection - ended");

        pipelineSplitOrchestrator.waitForPipelineSplittingEnded();
        logger.debug("test - pipeline splitting - ended");

        pipelinesManager.pipelineSplittingEnded();

        pipelinesManager.waitForExecutionEnd();
        logger.debug("test - execution - ended");
    }
}
