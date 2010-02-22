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
import org.gradle.api.testing.detection.DefaultTestClassScannerFactory;
import org.gradle.api.testing.detection.DefaultTestDetectionOrchestrator;
import org.gradle.api.testing.detection.TestDetectionOrchestrator;
import org.gradle.api.testing.execution.PipelineFactory;
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.pipelinesplit.TestPipelineSplitOrchestrator;
import org.gradle.api.testing.reporting.ReportsManager;
import org.gradle.api.testing.reporting.DefaultReportsManager;

/**
 * Creates the objects needed by the TestOrchestrator.
 *
 * @author Tom Eyckmans
 */
public class TestOrchestratorFactory {
    private final NativeTest testTask;

    /**
     * Initializes itself by creating a test detection queue, a fork control and a pipeline factory.
     *
     * @param testTask The test task that is run.
     */
    public TestOrchestratorFactory(NativeTest testTask) {
        this.testTask = testTask;
    }

    public TestOrchestratorContext createContext(final TestOrchestrator testOrchestrator) {
        ForkControl forkControl = new ForkControl(testTask.getMaximumNumberOfForks());
        PipelineFactory pipelineFactory = new PipelineFactory(testTask);
        TestPipelineSplitOrchestrator pipelineSplitOrchestrator = new TestPipelineSplitOrchestrator();
        DefaultTestClassScannerFactory testClassScannerFactory = new DefaultTestClassScannerFactory();
        ReportsManager reportOrchestrator = new DefaultReportsManager();
        TestDetectionOrchestrator detectionOrchestrator = new DefaultTestDetectionOrchestrator(
                testClassScannerFactory.createTestClassScanner(testTask, pipelineSplitOrchestrator.getProcessor(), null));
        PipelinesManager pipelinesManager = new PipelinesManager(pipelineFactory, forkControl, reportOrchestrator.getProcessor());

        return new TestOrchestratorContext(testOrchestrator, detectionOrchestrator, pipelineSplitOrchestrator,
                pipelinesManager, reportOrchestrator);
    }
}
