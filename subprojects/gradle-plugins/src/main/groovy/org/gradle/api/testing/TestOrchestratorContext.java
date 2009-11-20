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

import org.gradle.api.testing.detection.TestDetectionOrchestrator;
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.pipelinesplit.TestPipelineSplitOrchestrator;
import org.gradle.api.testing.reporting.ReportsManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Used to pass around the items needed by the test orchestrator actions.
 *
 * @author Tom Eyckmans
 */
public class TestOrchestratorContext {
    private final AtomicBoolean keepRunning = new AtomicBoolean(true);
    private final TestOrchestrator testOrchestrator;
    private final TestDetectionOrchestrator testDetectionOrchestrator;
    private final TestPipelineSplitOrchestrator pipelineSplitOrchestrator;
    private final PipelinesManager pipelinesManager;
    private final ReportsManager reportsManager;

    public TestOrchestratorContext(TestOrchestrator testOrchestrator,
                                   TestDetectionOrchestrator testDetectionOrchestrator,
                                   TestPipelineSplitOrchestrator pipelineSplitOrchestrator,
                                   PipelinesManager pipelinesManager, ReportsManager reportsManager) {
        if (testOrchestrator == null) {
            throw new IllegalArgumentException("testOrchestrator == null!");
        }
        if (testDetectionOrchestrator == null) {
            throw new IllegalArgumentException("testDetectionOrchestrator == null!");
        }
        if (pipelineSplitOrchestrator == null) {
            throw new IllegalArgumentException("pipelineSplitOrchestrator == null!");
        }
        if (pipelinesManager == null) {
            throw new IllegalArgumentException("pipelinesManager == null!");
        }
        if (reportsManager == null) {
            throw new IllegalArgumentException("reportOrchestrator == null!");
        }

        this.testOrchestrator = testOrchestrator;
        this.testDetectionOrchestrator = testDetectionOrchestrator;
        this.pipelineSplitOrchestrator = pipelineSplitOrchestrator;
        this.pipelinesManager = pipelinesManager;
        this.reportsManager = reportsManager;
    }

    public AtomicBoolean getKeepRunning() {
        return keepRunning;
    }

    public TestOrchestrator getTestOrchestrator() {
        return testOrchestrator;
    }

    public TestDetectionOrchestrator getTestDetectionOrchestrator() {
        return testDetectionOrchestrator;
    }

    public TestPipelineSplitOrchestrator getPipelineSplitOrchestrator() {
        return pipelineSplitOrchestrator;
    }

    public PipelinesManager getPipelinesManager() {
        return pipelinesManager;
    }

    public ReportsManager getReportsManager() {
        return reportsManager;
    }
}
