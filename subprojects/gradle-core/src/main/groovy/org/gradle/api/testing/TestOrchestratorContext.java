package org.gradle.api.testing;

import org.gradle.api.testing.detection.TestDetectionOrchestrator;
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.pipelinesplit.TestPipelineSplitOrchestrator;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Tom Eyckmans
 */
public class TestOrchestratorContext {
    private final AtomicBoolean keepRunning = new AtomicBoolean(true);
    private final TestOrchestrator testOrchestrator;
    private final TestDetectionOrchestrator testDetectionOrchestrator;
    private final TestPipelineSplitOrchestrator pipelineSplitOrchestrator;
    private final PipelinesManager pipelinesManager;

    public TestOrchestratorContext(TestOrchestrator testOrchestrator, TestDetectionOrchestrator testDetectionOrchestrator, TestPipelineSplitOrchestrator pipelineSplitOrchestrator, PipelinesManager pipelinesManager) {
        if ( testOrchestrator == null ) throw new IllegalArgumentException("testOrchestrator == null!");
        if ( testDetectionOrchestrator == null ) throw new IllegalArgumentException("testDetectionOrchestrator == null!");
        if ( pipelineSplitOrchestrator == null ) throw new IllegalArgumentException("pipelineSplitOrchestrator == null!");
        if ( pipelinesManager == null ) throw new IllegalArgumentException("pipelinesManager == null!");

        this.testOrchestrator = testOrchestrator;
        this.testDetectionOrchestrator = testDetectionOrchestrator;
        this.pipelineSplitOrchestrator = pipelineSplitOrchestrator;
        this.pipelinesManager = pipelinesManager;
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
}
