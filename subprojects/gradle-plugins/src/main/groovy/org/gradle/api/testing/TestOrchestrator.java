/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.testing.reporting.ReportsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls high level test execution.
 *
 * @author Tom Eyckmans
 */
public class TestOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestOrchestrator.class);
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
        this(testTask, new TestOrchestratorFactory(testTask));
    }

    /**
     * Constructor used for test purposes to mock out the test orchestrator factory or initialize the default test
     * orchestator factory with a different test detection queue size or with a custom test orchestator factory.
     *
     * @param testTask The test task that needs to be run.
     * @param factory The test orchestator factory to use.
     * @throws IllegalArgumentException when either testTask or factory are null.
     */
    public TestOrchestrator(final NativeTest testTask, TestOrchestratorFactory factory) {
        if (testTask == null) {
            throw new IllegalArgumentException("testTask == null!");
        }
        if (factory == null) {
            throw new IllegalArgumentException("factory == null!");
        }

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
     * Execution: When test are detected they advance to pipeline splitting. When pipeline splitting decides on which
     * pipeline to execute the test they advance to that pipeline. When a pipeline receives a test it executes it.
     */
    public void execute() {
        TestOrchestratorContext context = factory.createContext(this);

        PipelinesManager pipelinesManager = context.getPipelinesManager();
        ReportsManager reportsManager = context.getReportsManager();
        TestDetectionOrchestrator testDetectionOrchestrator = context.getTestDetectionOrchestrator();
        TestPipelineSplitOrchestrator pipelineSplitOrchestrator = context.getPipelineSplitOrchestrator();

        pipelinesManager.initialize(testTask.getPipelineConfigs());
        reportsManager.start(testTask.getReportConfigs(), testTask.getTestFramework());
        pipelineSplitOrchestrator.start(pipelinesManager);

        testDetectionOrchestrator.startDetection();
        testDetectionOrchestrator.waitForDetectionEnd();
        LOGGER.debug("test - detection - ended");

        pipelineSplitOrchestrator.waitForPipelineSplittingEnded();
        pipelinesManager.pipelineSplittingEnded();
        LOGGER.debug("test - pipeline splitting - ended");

        pipelinesManager.waitForExecutionEnd();
        reportsManager.waitForReportEnd();

        LOGGER.debug("test - execution - ended");
    }
}
